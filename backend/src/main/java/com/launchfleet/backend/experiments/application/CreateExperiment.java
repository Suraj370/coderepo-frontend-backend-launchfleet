package com.launchfleet.backend.experiments.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.activity.ActivityAction;
import com.launchfleet.backend.activity.ActivityRecorder;
import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.experiments.ports.ExperimentStore;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FlagStatus;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Creates a new DRAFT experiment (locked decision 1: a separate aggregate
 * referencing a FeatureFlag by id, never metadata attached to it). Configuration
 * (allocation, conversion event name) is set afterward via UpdateExperiment, before
 * StartExperiment - mirroring how CreateFeatureFlag creates a disabled config that
 * UpdateEnvironmentConfig/SetRollout configure afterward.
 */
@Component
public class CreateExperiment {

	private static final int CONFLICT = 409;

	private final ExperimentLookup lookup;

	private final ExperimentStore experimentStore;

	private final ActivityRecorder activityLogService;

	CreateExperiment(ExperimentLookup lookup, ExperimentStore experimentStore, ActivityRecorder activityLogService) {
		this.lookup = lookup;
		this.experimentStore = experimentStore;
		this.activityLogService = activityLogService;
	}

	public Experiment execute(String projectKey, String environmentKey, String flagKey, String key, String name,
			String description, String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);
		FeatureFlag flag = lookup.resolveFlag(project, flagKey);
		EnvironmentRef environment = lookup.resolveEnvironment(project, environmentKey);

		if (flag.getStatus() == FlagStatus.RETIRED) {
			throw new ApiException(CONFLICT, "FLAG_RETIRED", "A retired flag cannot have new experiments created for it.");
		}
		if (!environment.active()) {
			throw new ApiException(CONFLICT, "ENVIRONMENT_RETIRED",
					"A retired environment cannot have new experiments created for it.");
		}

		Experiment experiment = Experiment.create(project.id(), environment.id(), flag.getId(), key, name,
				description, actingUserId);
		Experiment saved = experimentStore.save(experiment);

		activityLogService.record(project.id(), actingUserId, ActivityAction.EXPERIMENT_CREATED, "experiment",
				saved.getKey(), environment.id());

		return saved;
	}
}
