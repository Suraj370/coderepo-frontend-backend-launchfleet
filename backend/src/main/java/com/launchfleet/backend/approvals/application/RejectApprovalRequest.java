package com.launchfleet.backend.approvals.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.activity.ActivityAction;
import com.launchfleet.backend.activity.ActivityRecorder;
import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/** PENDING -> REJECTED (locked architecture rule 17). Terminal - a rejected request can never be returned to PENDING. */
@Component
public class RejectApprovalRequest {

	private static final int BAD_REQUEST = 400;

	private static final int CONFLICT = 409;

	private final ApprovalRequestLookup lookup;

	private final ApprovalRequestStore approvalRequestStore;

	private final ActivityRecorder activityLogService;

	RejectApprovalRequest(ApprovalRequestLookup lookup, ApprovalRequestStore approvalRequestStore,
			ActivityRecorder activityLogService) {
		this.lookup = lookup;
		this.approvalRequestStore = approvalRequestStore;
		this.activityLogService = activityLogService;
	}

	public ApprovalRequest execute(String projectKey, String requestId, String reviewerId, String rejectionComment) {
		ProjectRef project = lookup.resolveProject(projectKey);
		ApprovalRequest request = lookup.resolveRequest(project, requestId);

		try {
			request.reject(reviewerId, rejectionComment);
		} catch (IllegalStateException exception) {
			throw new ApiException(CONFLICT, "INVALID_TRANSITION", exception.getMessage());
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}

		ApprovalRequest saved = approvalRequestStore.save(request);

		activityLogService.record(project.id(), reviewerId, ActivityAction.APPROVAL_REJECTED, "approval",
				saved.getFeatureFlagId(), saved.getEnvironmentId());

		return saved;
	}
}
