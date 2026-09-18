package com.launchfleet.backend.experiments.ports;

import com.launchfleet.backend.shared.ApiException;

/**
 * Thrown by ExperimentStore.save() when the persisted experiment's version has
 * already moved past what the caller observed - the same optimistic-concurrency
 * idiom as FeatureFlagConfig.version and ApprovalRequest.persistenceVersion (locked
 * decision 16: optimistic version checks, no transactions for ordinary Phase 7
 * operations). Extends ApiException so it surfaces as a plain 409 through the
 * existing GlobalExceptionHandler, with no changes needed there.
 */
public class ExperimentConflictException extends ApiException {

	private static final int CONFLICT = 409;

	public ExperimentConflictException(String message) {
		super(CONFLICT, "EXPERIMENT_CONFLICT", message);
	}
}
