package com.launchfleet.backend.experiments.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.experiments.ports.ExperimentConflictException;
import com.launchfleet.backend.experiments.ports.ExperimentStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagRetiredListener;

/**
 * Implements the EXISTING featureflags.ports.FeatureFlagRetiredListener (no changes
 * to featureflags/ - the exact same fan-out pattern approvals/ already uses):
 * cancels every DRAFT/RUNNING experiment for a flag the moment it is retired, so
 * none of them can be started/continue running against an invalid target (locked
 * decision 10/12). featureflags/ never needs to know experiments/ exists.
 *
 * A conflict here (another concurrent operation already changed this exact
 * experiment) is caught and skipped per-experiment rather than propagated - the
 * flag retirement itself must not be undermined by one cascaded cancellation
 * losing an optimistic-version race, mirroring
 * approvals.application.FlagRetirementCancellationListener's identical reasoning.
 */
@Component
class ExperimentFlagRetirementListener implements FeatureFlagRetiredListener {

	private static final String SYSTEM_ACTOR = "system";

	private final ExperimentStore experimentStore;

	ExperimentFlagRetirementListener(ExperimentStore experimentStore) {
		this.experimentStore = experimentStore;
	}

	@Override
	public void onFeatureFlagRetired(String projectId, String featureFlagId, String flagKey, String actingUserId) {
		for (Experiment experiment : experimentStore.findActiveByFeatureFlagId(featureFlagId)) {
			try {
				experiment.cancel(SYSTEM_ACTOR);
				experimentStore.save(experiment);
			} catch (ExperimentConflictException conflict) {
				// Already resolved by a concurrent operation - nothing more to do for it.
			}
		}
	}
}
