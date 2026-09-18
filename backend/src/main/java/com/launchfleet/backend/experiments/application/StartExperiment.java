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
 * DRAFT -> RUNNING. Experiment.start() itself enforces that a complete, valid
 * configuration exists (locked decision 12); this use case additionally re-checks
 * the referenced flag/environment are still active - an experiment must not start
 * against an already-invalid target, even if the retirement cascade (see
 * ExperimentFlagRetirementListener/ExperimentEnvironmentRetirementListener) hasn't
 * caught up yet for some reason.
 */
@Component
public class StartExperiment {

	private static final int CONFLICT = 409;

	private final ExperimentLookup lookup;

	private final ExperimentStore experimentStore;

	private final ActivityRecorder activityLogService;

	StartExperiment(ExperimentLookup lookup, ExperimentStore experimentStore, ActivityRecorder activityLogService) {
		this.lookup = lookup;
		this.experimentStore = experimentStore;
		this.activityLogService = activityLogService;
	}

	public Experiment execute(String projectKey, String experimentKey, String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);
		Experiment experiment = lookup.resolveExperiment(project, experimentKey);
		FeatureFlag flag = lookup.resolveFlagById(experiment.getFeatureFlagId());
		EnvironmentRef environment = lookup.resolveEnvironmentById(experiment.getEnvironmentId());

		if (flag.getStatus() == FlagStatus.RETIRED) {
			throw new ApiException(CONFLICT, "FLAG_RETIRED", "A retired flag's experiment cannot be started.");
		}
		if (!environment.active()) {
			throw new ApiException(CONFLICT, "ENVIRONMENT_RETIRED", "A retired environment's experiment cannot be started.");
		}

		try {
			experiment.start(actingUserId);
		} catch (IllegalStateException exception) {
			throw new ApiException(CONFLICT, "INVALID_TRANSITION", exception.getMessage());
		}

		Experiment saved = experimentStore.save(experiment);

		activityLogService.record(project.id(), actingUserId, ActivityAction.EXPERIMENT_STARTED, "experiment",
				saved.getKey(), environment.id());

		return saved;
	}
}
