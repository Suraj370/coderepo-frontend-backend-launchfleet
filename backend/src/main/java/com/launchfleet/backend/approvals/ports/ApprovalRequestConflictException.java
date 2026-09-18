package com.launchfleet.backend.approvals.ports;

import com.launchfleet.backend.shared.ApiException;

/**
 * Thrown by ApprovalRequestStore.save() when the persisted document's
 * persistenceVersion has already moved past what the caller observed - i.e. a
 * concurrent operation (another reviewer's approve/reject, a submitter's cancel, a
 * second ScheduledApprovalPoller execution, a retirement cascade) already won the
 * race and persisted its own transition first. This save's changes are NOT applied;
 * the caller's in-memory ApprovalRequest is now stale and must not be retried blindly
 * (retrying would need to reload and re-validate against the new persisted state).
 *
 * Extends ApiException (409) so it flows through the existing GlobalExceptionHandler
 * unchanged for HTTP-facing use cases (ApproveApprovalRequest, RejectApprovalRequest,
 * ScheduleApprovalRequest, CancelApprovalRequest) - the API contract stays a plain
 * 409 JSON error envelope, just with this specific `code`. Internal, non-HTTP callers
 * (ApplyDueScheduledApprovals, the retirement listeners) catch this SPECIFIC type to
 * tell "someone else already resolved this" apart from a genuine unexpected failure -
 * see their Javadoc for why that distinction matters (a lost race is not a FAILED
 * outcome and not a reason to abort a retirement cascade).
 */
public class ApprovalRequestConflictException extends ApiException {

	private static final int CONFLICT = 409;

	public ApprovalRequestConflictException(String message) {
		super(CONFLICT, "APPROVAL_REQUEST_CONFLICT", message);
	}
}
