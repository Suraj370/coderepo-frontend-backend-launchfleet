package com.launchfleet.backend.approvals.domain;

/**
 * The complete, locked status set for Phase 6 - no STALE status exists (see
 * CancellationReason.STALE_CONFIGURATION instead). Valid transitions are enforced
 * centrally by ApprovalRequest itself, not scattered across use cases (see its
 * Javadoc).
 */
public enum ApprovalStatus {

	PENDING,

	APPROVED,

	REJECTED,

	SCHEDULED,

	APPLIED,

	CANCELLED,

	FAILED

}
