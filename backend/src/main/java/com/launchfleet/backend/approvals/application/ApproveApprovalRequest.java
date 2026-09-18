package com.launchfleet.backend.approvals.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.activity.ActivityAction;
import com.launchfleet.backend.activity.ActivityRecorder;
import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.domain.CancellationReason;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Immediate approval (locked architecture rule 10): PENDING -> approve() -> apply ->
 * APPLIED, all in one call. If the proposal turns out to be stale, or its flag/
 * environment was retired since submission, this cancels it instead of applying -
 * never silently applies over newer configuration (rule 20).
 *
 * Persists the approve() decision itself (a real, conditional PENDING -> APPROVED
 * write) BEFORE calling ApplyProposedConfiguration.apply, which is the one thing
 * this use case does that has a side effect outside this aggregate (a
 * FeatureFlagConfig write). Two things depend on that ordering:
 * <ul>
 * <li>two concurrent approve attempts on the same request both try this same
 * conditional write; only one can succeed, so only one ever reaches the config
 * write at all - no duplicate application, and no risk of the eventual
 * ApprovalRequest-save race being won by whichever attempt LOST the config race;</li>
 * <li>while APPROVED is persisted, CancelApprovalRequest's own authorization check
 * and RejectApprovalRequest/the retirement-cascade listeners' own status
 * preconditions and queries all only ever act on PENDING or SCHEDULED requests - so
 * none of them can race in and cancel/reject this request out from under an
 * in-flight config write. A flag/environment retirement that lands during this
 * narrow window simply won't find this request in its PENDING/SCHEDULED query, and
 * ApplyProposedConfiguration itself still re-checks the flag/environment's current
 * status at apply time regardless.</li>
 * </ul>
 * This briefly makes APPROVED a real, persisted status - narrower than "never
 * persisted at all", but necessary: without it, a concurrent cancel/reject/
 * retirement can still observe PENDING and win the FINAL ApprovalRequest-save race
 * after this use case's config write has already happened, leaving the record
 * showing CANCELLED/REJECTED while the configuration was still actually mutated. It
 * remains true that no caller of this API can ever OBSERVE a persisted APPROVED
 * response - this method always continues past it to APPLIED or a cancellation in
 * the same call. ApplyDueScheduledApprovals uses an analogous claim step for the
 * scheduled path (see its Javadoc for the narrower guarantee that gives, since
 * SCHEDULED has no equivalent intermediate status available to persist through).
 */
@Component
public class ApproveApprovalRequest {

	private static final int BAD_REQUEST = 400;

	private static final int CONFLICT = 409;

	private final ApprovalRequestLookup lookup;

	private final ApprovalRequestStore approvalRequestStore;

	private final ApplyProposedConfiguration applyProposedConfiguration;

	private final ActivityRecorder activityLogService;

	ApproveApprovalRequest(ApprovalRequestLookup lookup, ApprovalRequestStore approvalRequestStore,
			ApplyProposedConfiguration applyProposedConfiguration, ActivityRecorder activityLogService) {
		this.lookup = lookup;
		this.approvalRequestStore = approvalRequestStore;
		this.applyProposedConfiguration = applyProposedConfiguration;
		this.activityLogService = activityLogService;
	}

	public ApprovalRequest execute(String projectKey, String requestId, String reviewerId, String approvalComment) {
		ProjectRef project = lookup.resolveProject(projectKey);
		ApprovalRequest loaded = lookup.resolveRequest(project, requestId);

		try {
			loaded.approve(reviewerId, approvalComment);
		} catch (IllegalStateException exception) {
			throw new ApiException(CONFLICT, "INVALID_TRANSITION", exception.getMessage());
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}

		// Persists PENDING -> APPROVED for real, conditioned on the persistenceVersion
		// this call observed - see this class's Javadoc for why this specific ordering
		// (persist the decision, THEN touch FeatureFlagConfig) is what closes the races
		// this remediation exists for. Throws ApprovalRequestConflictException (a 409
		// ApiException) if another operation already changed this request since it was
		// loaded - before any config write ever happens.
		ApprovalRequest request = approvalRequestStore.save(loaded);

		ApplyOutcome outcome = applyProposedConfiguration.apply(request);

		boolean actuallyApplied = outcome instanceof ApplyOutcome.Applied;

		if (outcome instanceof ApplyOutcome.Applied applied) {
			request.applyNow(applied.newVersion());
		} else if (outcome instanceof ApplyOutcome.Stale) {
			request.cancel(reviewerId, CancellationReason.STALE_CONFIGURATION);
		} else if (outcome instanceof ApplyOutcome.FlagRetired) {
			request.cancel(reviewerId, CancellationReason.FLAG_RETIRED);
		} else if (outcome instanceof ApplyOutcome.EnvironmentRetired) {
			request.cancel(reviewerId, CancellationReason.ENVIRONMENT_RETIRED);
		} else if (outcome instanceof ApplyOutcome.Failed) {
			// applyNow/applyScheduled were never reached, so the request is still APPROVED
			// in memory - nothing has been persisted yet, unlike the scheduled path (see
			// ApplyDueScheduledApprovals), so there is no FAILED row to leave behind here;
			// the caller simply sees an error and the request remains PENDING to retry.
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR",
					"The proposed configuration could not be applied: " + ((ApplyOutcome.Failed) outcome).reason());
		}

		ApprovalRequest saved = approvalRequestStore.save(request);

		if (actuallyApplied) {
			activityLogService.record(project.id(), reviewerId, ActivityAction.APPROVAL_APPROVED, "approval",
					saved.getFeatureFlagId(), saved.getEnvironmentId());
		}

		return saved;
	}
}
