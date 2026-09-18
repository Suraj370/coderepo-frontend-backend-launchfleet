package com.launchfleet.backend.approvals.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.domain.CancellationReason;
import com.launchfleet.backend.approvals.ports.ApprovalRequestConflictException;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagRetiredListener;

/**
 * Implements featureflags.ports.FeatureFlagRetiredListener (locked architecture rule
 * 15): cancels every PENDING/SCHEDULED approval request for a flag the moment it is
 * retired, so none of them can be approved/applied afterward. featureflags/ never
 * needs to know approvals/ exists - it just notifies every registered listener (see
 * RetireFeatureFlag).
 *
 * A single request's save() can lose the same persistenceVersion race any other
 * lifecycle operation can (see ApprovalRequest's Javadoc) - e.g. an ADMIN approves
 * the exact request this listener is trying to cancel, in the same instant the flag
 * is retired. That conflict is caught and skipped per-request rather than propagated:
 * the flag retirement itself (RetireFeatureFlag, which already succeeded before this
 * listener runs) must not be undermined by one cascaded cancellation losing a race,
 * and whichever operation won that race already left the request in a valid,
 * deliberately-decided state.
 *
 * This plain single-document save is deliberately NOT wrapped in its own explicit
 * transaction. It doesn't need to be: MongoDB has always made a single document's
 * writes atomic, and - critically for racing against ApplyDueScheduledApprovals'
 * multi-document transaction (see its Javadoc) - MongoDB's transaction engine
 * already serializes any write to a document that a concurrent, still-open
 * transaction has also written: whichever commits first wins, the other conflicts.
 * That is what makes it impossible for this cancel() to succeed against a request
 * whose scheduled application has already committed, and equally impossible for an
 * already-committed cancellation here to be silently overwritten by a scheduled
 * application that started before it but finishes after.
 */
@Component
class FlagRetirementCancellationListener implements FeatureFlagRetiredListener {

	private static final Logger log = LoggerFactory.getLogger(FlagRetirementCancellationListener.class);

	private static final String SYSTEM_ACTOR = "system";

	private final ApprovalRequestStore approvalRequestStore;

	FlagRetirementCancellationListener(ApprovalRequestStore approvalRequestStore) {
		this.approvalRequestStore = approvalRequestStore;
	}

	@Override
	public void onFeatureFlagRetired(String projectId, String featureFlagId, String flagKey, String actingUserId) {
		for (ApprovalRequest request : approvalRequestStore.findPendingOrScheduledByFeatureFlagId(featureFlagId)) {
			try {
				request.cancel(SYSTEM_ACTOR, CancellationReason.FLAG_RETIRED);
				approvalRequestStore.save(request);
			} catch (ApprovalRequestConflictException conflict) {
				log.debug("Approval request {} was already resolved by a concurrent operation; "
						+ "flag-retirement cancellation skipped for it.", request.getId());
			}
		}
	}
}
