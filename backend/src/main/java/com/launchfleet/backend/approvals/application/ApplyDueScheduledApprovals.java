package com.launchfleet.backend.approvals.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.domain.CancellationReason;
import com.launchfleet.backend.approvals.ports.ApprovalRequestConflictException;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.mongodb.MongoException;

/**
 * The scheduled-execution core (locked architecture rule 12/13) - a plain,
 * synchronously callable method with no @Scheduled annotation of its own, so tests
 * invoke it directly against a fixed Clock rather than waiting on
 * ScheduledApprovalPoller's real timer (rule 25). ScheduledApprovalPoller is the only
 * production caller.
 *
 * "system" is used as the actor for every automatic outcome here (cancellation or
 * failure) - none of these are a live human decision at the moment they happen,
 * unlike reviewedBy (still attributed to whoever originally approved/scheduled the
 * request) or a submitter/admin-initiated cancel (see CancelApprovalRequest).
 *
 * CROSS-AGGREGATE ATOMICITY (the scheduler-vs-retirement remediation): the claim,
 * the FeatureFlagConfig write (via ApplyProposedConfiguration), and the final
 * ApprovalRequest write all happen inside ONE real MongoDB multi-document
 * transaction (see ApprovalTransactionConfig - requires the configured MongoDB to be
 * a replica set). This is what makes the following actually impossible, not just
 * unlikely:
 * <ul>
 * <li><b>scheduler commits, then retirement cancels anyway</b> - can't happen: once
 * this transaction commits, the ApprovalRequest document already reflects APPLIED
 * with the new persistenceVersion. A retirement cascade reading afterward sees
 * APPLIED (not PENDING/SCHEDULED), so its own query for PENDING/SCHEDULED requests
 * simply never returns this one.</li>
 * <li><b>retirement cancels, then scheduler applies anyway</b> - can't happen: if
 * retirement's single-document cancel commits before this transaction reaches its
 * own commit point, this transaction's write to that same document conflicts
 * (MongoDB's transaction engine detects the concurrent modification) and the WHOLE
 * transaction rolls back - the FeatureFlagConfig write is undone with it. Nothing
 * is left half-applied.</li>
 * <li><b>both "commit" concurrently</b> - can't happen: MongoDB transactions
 * serialize writers of the same document; exactly one of {this transaction,
 * retirement's cancel} commits, the other aborts/conflicts and is retried or
 * abandoned (see processOne).</li>
 * </ul>
 * Retirement's own cancellation (FlagRetirementCancellationListener/
 * EnvironmentRetirementCancellationListener) does NOT need its own explicit
 * transaction for this guarantee to hold - a single-document write has always been
 * atomic in MongoDB, transactions or not, and a multi-document transaction's writes
 * to a document already involved in another in-flight transaction are exactly what
 * MongoDB's transaction engine exists to serialize.
 *
 * Two executions of this method (two poller ticks, or two application instances
 * once this runs with more than one) can still both fetch the same due SCHEDULED
 * request; the same transactional claim decides between them exactly like it
 * decides between this execution and a racing retirement.
 */
@Component
public class ApplyDueScheduledApprovals {

	private static final Logger log = LoggerFactory.getLogger(ApplyDueScheduledApprovals.class);

	private static final String SYSTEM_ACTOR = "system";

	/**
	 * MongoDB explicitly recommends retrying a transaction that fails with a
	 * TransientTransactionError label (e.g. a write conflict against another
	 * concurrent transaction) - it is not a real failure, just contention. Bounded
	 * deliberately (locked architecture instruction: no uncontrolled retry loop) -
	 * after this many attempts, give up and let the request be picked up by a later
	 * poll instead of retrying forever.
	 */
	private static final int MAX_TRANSACTION_ATTEMPTS = 3;

	private final ApprovalRequestStore approvalRequestStore;

	private final ApplyProposedConfiguration applyProposedConfiguration;

	private final Clock clock;

	private final TransactionTemplate transactionTemplate;

	ApplyDueScheduledApprovals(ApprovalRequestStore approvalRequestStore,
			ApplyProposedConfiguration applyProposedConfiguration, Clock clock,
			TransactionTemplate scheduledApprovalTransactionTemplate) {
		this.approvalRequestStore = approvalRequestStore;
		this.applyProposedConfiguration = applyProposedConfiguration;
		this.clock = clock;
		this.transactionTemplate = scheduledApprovalTransactionTemplate;
	}

	/** Processes every SCHEDULED request whose scheduledAt has arrived. Returns how many were processed, for tests/diagnostics. */
	public int execute() {
		Instant now = Instant.now(clock);
		List<ApprovalRequest> due = approvalRequestStore.findScheduledAtOrBefore(now);

		for (ApprovalRequest request : due) {
			processOne(request);
		}

		return due.size();
	}

	private void processOne(ApprovalRequest loaded) {
		for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
			try {
				transactionTemplate.executeWithoutResult(status -> processOneTransactionally(loaded));
				return;
			} catch (ApprovalRequestConflictException alreadyResolved) {
				// The expected, benign outcome of losing a race (another scheduler execution,
				// or - the remediation this class exists for - a retirement cascade that
				// cancelled this exact request first). Nothing was committed. Not a failure.
				log.debug("Approval request {} was already resolved by a concurrent operation; skipping.",
						loaded.getId());
				return;
			} catch (RuntimeException exception) {
				if (isTransientTransactionError(exception)) {
					if (attempt < MAX_TRANSACTION_ATTEMPTS) {
						log.debug("Transient transaction conflict applying approval request {} (attempt {}/{}); retrying.",
								loaded.getId(), attempt, MAX_TRANSACTION_ATTEMPTS);
						continue;
					}
					log.warn("Approval request {} still conflicting after {} attempts; leaving it for a later poll.",
							loaded.getId(), MAX_TRANSACTION_ATTEMPTS);
					return;
				}

				handleUnexpectedFailure(loaded, exception);
				return;
			}
		}
	}

	/**
	 * The transactional body: claim, apply, and record the outcome - see this class's
	 * Javadoc for why all three happen in one transaction. Runs on the transaction-
	 * bound MongoDB session automatically (Spring Data MongoDB binds it to the current
	 * thread for the duration of the callback), so approvalRequestStore.save and
	 * ApplyProposedConfiguration.apply's own FeatureFlagConfig write both participate
	 * without any change to those classes themselves.
	 */
	private void processOneTransactionally(ApprovalRequest loaded) {
		// The claim: fails fast (before any FeatureFlagConfig write) if the document has
		// already moved - including via a retirement cancellation that landed first.
		ApprovalRequest request = approvalRequestStore.save(loaded);

		ApplyOutcome outcome = applyProposedConfiguration.apply(request);

		if (outcome instanceof ApplyOutcome.Applied applied) {
			request.applyScheduled(applied.newVersion());
		} else if (outcome instanceof ApplyOutcome.Stale) {
			request.cancel(SYSTEM_ACTOR, CancellationReason.STALE_CONFIGURATION);
		} else if (outcome instanceof ApplyOutcome.FlagRetired) {
			request.cancel(SYSTEM_ACTOR, CancellationReason.FLAG_RETIRED);
		} else if (outcome instanceof ApplyOutcome.EnvironmentRetired) {
			request.cancel(SYSTEM_ACTOR, CancellationReason.ENVIRONMENT_RETIRED);
		} else if (outcome instanceof ApplyOutcome.Failed failed) {
			request.fail();
			log.error("Scheduled application failed for approval request {} (flag {}, environment {}): {}",
					request.getId(), request.getFeatureFlagId(), request.getEnvironmentId(), failed.reason());
		}

		// Still inside the transaction: this commits (or, on conflict, aborts) together
		// with whatever ApplyProposedConfiguration.apply just wrote.
		approvalRequestStore.save(request);
	}

	/**
	 * No automatic retry (locked architecture rule 13) - mark FAILED and move on
	 * rather than letting one bad request abort the whole scheduled batch. This is a
	 * plain, separate, non-transactional save: there is no FeatureFlagConfig write to
	 * coordinate with here (the transaction above already rolled back whatever it had
	 * done), so FAILED needs no cross-aggregate atomicity, only the ordinary
	 * persistenceVersion-conditioned save every other transition already uses. Logs
	 * the exception type/message only, never the proposed configuration's content.
	 */
	private void handleUnexpectedFailure(ApprovalRequest loaded, RuntimeException exception) {
		log.error("Unexpected error applying scheduled approval request {} (flag {}, environment {}): {}",
				loaded.getId(), loaded.getFeatureFlagId(), loaded.getEnvironmentId(),
				exception.getClass().getSimpleName() + ": " + exception.getMessage());
		try {
			loaded.fail();
			approvalRequestStore.save(loaded);
		} catch (RuntimeException persistFailure) {
			log.error("Could not persist FAILED status for approval request {}: {}", loaded.getId(),
					persistFailure.getClass().getSimpleName());
		}
	}

	private static boolean isTransientTransactionError(Throwable exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof MongoException mongoException
					&& mongoException.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
				return true;
			}
			cause = cause.getCause();
		}

		return false;
	}
}
