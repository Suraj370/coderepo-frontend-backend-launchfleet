package com.launchfleet.backend.approvals.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.domain.ApprovalStatus;
import com.launchfleet.backend.approvals.domain.CancellationReason;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Cancellation (locked architecture rules 5/14). The controller's @PreAuthorize only
 * gates "at least EDITOR" - a VIEWER can never reach this at all. The finer-grained
 * rule that a VIEWER can't apply to (ownership vs. ADMIN authority) lives here,
 * exactly like the submitter/reviewer distinction lives in ApprovalRequest.approve
 * rather than in a @PreAuthorize expression, since it depends on data
 * (submittedBy/status), not just role:
 * <ul>
 * <li>the original submitter may cancel their own PENDING or SCHEDULED request;</li>
 * <li>an ADMIN may cancel any SCHEDULED request (not PENDING - see rule 5's ADMIN
 * permission list, which grants "cancel scheduled requests" specifically; an ADMIN
 * who wants to kill someone else's PENDING request rejects it instead);</li>
 * <li>anyone else (an EDITOR who is neither the submitter nor an ADMIN) is
 * forbidden.</li>
 * </ul>
 * Both paths record cancellationReason=ADMIN_CANCELLED (rule 14 does not distinguish
 * submitter- from admin-initiated cancellation with a separate reason code).
 */
@Component
public class CancelApprovalRequest {

	private static final int FORBIDDEN = 403;

	private static final int CONFLICT = 409;

	private final ApprovalRequestLookup lookup;

	private final ApprovalRequestStore approvalRequestStore;

	CancelApprovalRequest(ApprovalRequestLookup lookup, ApprovalRequestStore approvalRequestStore) {
		this.lookup = lookup;
		this.approvalRequestStore = approvalRequestStore;
	}

	public ApprovalRequest execute(String projectKey, String requestId, String actingUserId, boolean actingUserIsAdmin) {
		ProjectRef project = lookup.resolveProject(projectKey);
		ApprovalRequest request = lookup.resolveRequest(project, requestId);

		boolean isSubmitter = request.getSubmittedBy().equals(actingUserId);
		boolean submitterCanCancel = isSubmitter
				&& (request.getStatus() == ApprovalStatus.PENDING || request.getStatus() == ApprovalStatus.SCHEDULED);
		boolean adminCanCancel = actingUserIsAdmin && request.getStatus() == ApprovalStatus.SCHEDULED;

		if (!submitterCanCancel && !adminCanCancel) {
			throw new ApiException(FORBIDDEN, "CANNOT_CANCEL",
					"You do not have permission to cancel this request in its current state.");
		}

		try {
			request.cancel(actingUserId, CancellationReason.ADMIN_CANCELLED);
		} catch (IllegalStateException exception) {
			throw new ApiException(CONFLICT, "INVALID_TRANSITION", exception.getMessage());
		}

		return approvalRequestStore.save(request);
	}
}
