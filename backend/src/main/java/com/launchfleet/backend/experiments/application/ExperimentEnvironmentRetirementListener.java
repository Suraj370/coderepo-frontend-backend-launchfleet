package com.launchfleet.backend.experiments.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.experiments.ports.ExperimentConflictException;
import com.launchfleet.backend.experiments.ports.ExperimentStore;
import com.launchfleet.backend.environments.ports.EnvironmentRetiredListener;

/** Mirrors ExperimentFlagRetirementListener exactly, for environments/ (no changes to environments/ - the existing port is reused as-is). */
@Component
class ExperimentEnvironmentRetirementListener implements EnvironmentRetiredListener {

	private static final String SYSTEM_ACTOR = "system";

	private final ExperimentStore experimentStore;

	ExperimentEnvironmentRetirementListener(ExperimentStore experimentStore) {
		this.experimentStore = experimentStore;
	}

	@Override
	public void onEnvironmentRetired(String projectId, String environmentId, String environmentKey,
			String actingUserId) {
		for (Experiment experiment : experimentStore.findActiveByEnvironmentId(environmentId)) {
			try {
				experiment.cancel(SYSTEM_ACTOR);
				experimentStore.save(experiment);
			} catch (ExperimentConflictException conflict) {
				// Already resolved by a concurrent operation - nothing more to do for it.
			}
		}
	}
}
