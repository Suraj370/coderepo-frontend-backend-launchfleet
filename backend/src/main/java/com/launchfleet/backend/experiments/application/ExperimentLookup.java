package com.launchfleet.backend.experiments.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.experiments.ports.ExperimentStore;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.ports.EnvironmentLookup;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;
import com.launchfleet.backend.featureflags.ports.ProjectLookup;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Shared, mechanical resolution ("load this or 404") used by every experiments use
 * case - mirrors approvals.application.ApprovalRequestLookup's role exactly, reusing
 * the SAME featureflags ports (ProjectLookup, EnvironmentLookup, FeatureFlagStore)
 * rather than a second set of project/environment/flag lookup abstractions. The
 * projects/ and featureflags/ modules are never modified for this.
 */
@Component
class ExperimentLookup {

	private static final int NOT_FOUND = 404;

	private final ProjectLookup projectLookup;

	private final EnvironmentLookup environmentLookup;

	private final FeatureFlagStore featureFlagStore;

	private final ExperimentStore experimentStore;

	ExperimentLookup(ProjectLookup projectLookup, EnvironmentLookup environmentLookup,
			FeatureFlagStore featureFlagStore, ExperimentStore experimentStore) {
		this.projectLookup = projectLookup;
		this.environmentLookup = environmentLookup;
		this.featureFlagStore = featureFlagStore;
		this.experimentStore = experimentStore;
	}

	ProjectRef resolveProject(String projectKey) {
		return projectLookup.findByKey(projectKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "PROJECT_NOT_FOUND", "No project with that key."));
	}

	FeatureFlag resolveFlag(ProjectRef project, String flagKey) {
		return featureFlagStore.findByProjectIdAndKey(project.id(), flagKey).orElseThrow(
				() -> new ApiException(NOT_FOUND, "FLAG_NOT_FOUND", "No flag with that key in this project."));
	}

	/** For use cases that only have an Experiment's stored featureFlagId, not the caller-supplied flag key. */
	FeatureFlag resolveFlagById(String featureFlagId) {
		return featureFlagStore.findById(featureFlagId)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "FLAG_NOT_FOUND", "The referenced flag no longer exists."));
	}

	EnvironmentRef resolveEnvironment(ProjectRef project, String environmentKey) {
		return environmentLookup.findByProjectIdAndKey(project.id(), environmentKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "ENVIRONMENT_NOT_FOUND",
						"No environment '" + environmentKey + "' in this project."));
	}

	/** For use cases that only have an Experiment's stored environmentId, not the caller-supplied environment key. */
	EnvironmentRef resolveEnvironmentById(String environmentId) {
		return environmentLookup.findById(environmentId).orElseThrow(() -> new ApiException(NOT_FOUND,
				"ENVIRONMENT_NOT_FOUND", "The referenced environment no longer exists."));
	}

	/** experimentKey is trusted to belong to `project` only after this check - see every use case that calls it. */
	Experiment resolveExperiment(ProjectRef project, String experimentKey) {
		Experiment experiment = experimentStore.findByProjectIdAndKey(project.id(), experimentKey).orElseThrow(
				() -> new ApiException(NOT_FOUND, "EXPERIMENT_NOT_FOUND", "No experiment with that key."));

		if (!experiment.getProjectId().equals(project.id())) {
			throw new ApiException(NOT_FOUND, "EXPERIMENT_NOT_FOUND", "No experiment with that key.");
		}

		return experiment;
	}
}
