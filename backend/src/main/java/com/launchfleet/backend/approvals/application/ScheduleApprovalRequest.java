package com.launchfleet.backend.approvals.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.domain.CancellationReason;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Approve-and-schedule (locked architecture rule 11): PENDING -> approve() ->
 * SCHEDULED. No configuration is written yet - ApplyDueScheduledApprovals performs
 * the actual application at scheduledAt (rule 12). Still performs the same stale/
 * retirement pre-check approval does (rule 8), since scheduling a proposal that is
 * already stale would just delay discovering that until execution time for no
 * benefit.
 */
@Component
public class ScheduleApprovalRequest {

	private static final int BAD_REQUEST = 400;

	private static final int CONFLICT = 409;

	private final ApprovalRequestLookup lookup;

	private final ApprovalRequestStore approvalRequestStore;

	private final ApplyProposedConfiguration applyProposedConfiguration;

	private final Clock clock;

	ScheduleApprovalRequest(ApprovalRequestLookup lookup, ApprovalRequestStore approvalRequestStore,
			ApplyProposedConfiguration applyProposedConfiguration, Clock clock) {
		this.lookup = lookup;
		this.approvalRequestStore = approvalRequestStore;
		this.applyProposedConfiguration = applyProposedConfiguration;
		this.clock = clock;
	}

	public ApprovalRequest execute(String projectKey, String requestId, String reviewerId, String approvalComment,
			Instant scheduledAt) {
		ProjectRef project = lookup.resolveProject(projectKey);
		ApprovalRequest request = lookup.resolveRequest(project, requestId);

		try {
			request.approve(reviewerId, approvalComment);
		} catch (IllegalStateException exception) {
			throw new ApiException(CONFLICT, "INVALID_TRANSITION", exception.getMessage());
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}

		ApplyOutcome outcome = applyProposedConfiguration.checkApplicability(request);

		if (outcome instanceof ApplyOutcome.Applied) {
			try {
				request.scheduleFor(scheduledAt, Instant.now(clock));
			} catch (IllegalArgumentException exception) {
				throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
			}
		} else if (outcome instanceof ApplyOutcome.Stale) {
			request.cancel(reviewerId, CancellationReason.STALE_CONFIGURATION);
		} else if (outcome instanceof ApplyOutcome.FlagRetired) {
			request.cancel(reviewerId, CancellationReason.FLAG_RETIRED);
		} else if (outcome instanceof ApplyOutcome.EnvironmentRetired) {
			request.cancel(reviewerId, CancellationReason.ENVIRONMENT_RETIRED);
		}

		return approvalRequestStore.save(request);
	}
}
