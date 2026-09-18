package com.launchfleet.backend.approvals.domain;

/**
 * Structured, not free-form (Phase 6 locks this down explicitly). ADMIN_CANCELLED
 * covers both an ADMIN cancelling a SCHEDULED request and a submitter cancelling
 * their own PENDING/SCHEDULED request - the spec does not distinguish those with a
 * separate reason code, so neither does this enum.
 */
public enum CancellationReason {

	STALE_CONFIGURATION,

	FLAG_RETIRED,

	ENVIRONMENT_RETIRED,

	ADMIN_CANCELLED

}
