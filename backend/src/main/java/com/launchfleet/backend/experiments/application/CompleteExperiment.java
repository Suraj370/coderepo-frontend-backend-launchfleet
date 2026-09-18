package com.launchfleet.backend.experiments.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.activity.ActivityAction;
import com.launchfleet.backend.activity.ActivityRecorder;
import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.experiments.ports.ExperimentStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/** RUNNING -> COMPLETED: a deliberate stop, distinct from the retirement-triggered CANCELLED path. */
@Component
public class CompleteExperiment {

	private static final int CONFLICT = 409;

	private final ExperimentLookup lookup;

	private final ExperimentStore experimentStore;

	private final ActivityRecorder activityLogService;

	CompleteExperiment(ExperimentLookup lookup, ExperimentStore experimentStore,
			ActivityRecorder activityLogService) {
		this.lookup = lookup;
		this.experimentStore = experimentStore;
		this.activityLogService = activityLogService;
	}

	public Experiment execute(String projectKey, String experimentKey, String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);
		Experiment experiment = lookup.resolveExperiment(project, experimentKey);

		try {
			experiment.complete(actingUserId);
		} catch (IllegalStateException exception) {
			throw new ApiException(CONFLICT, "INVALID_TRANSITION", exception.getMessage());
		}

		Experiment saved = experimentStore.save(experiment);

		activityLogService.record(project.id(), actingUserId, ActivityAction.EXPERIMENT_COMPLETED, "experiment",
				saved.getKey(), saved.getEnvironmentId());

		return saved;
	}
}
