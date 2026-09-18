package com.launchfleet.backend.featureflags.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.ports.EnvironmentLookup;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;
import com.launchfleet.backend.featureflags.ports.ProjectLookup;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Shared, mechanical resolution/view-assembly used by several use cases - not a
 * business-rule home (no domain decisions are made here, only "load this or 404" and
 * "bundle these already-loaded things together"). This is what keeps CreateFeatureFlag,
 * GetFeatureFlag, UpdateFeatureFlag, UpdateEnvironmentConfig, and RetireFeatureFlag from
 * each re-deriving the same lookups, without becoming a do-everything service: every
 * operation-specific decision (variant rules, lifecycle transitions, versioning) still
 * lives in the domain or in the use case that owns that operation.
 */
@Component
class FeatureFlagLookup {

	private static final int NOT_FOUND = 404;

	private final FeatureFlagStore featureFlagStore;

	private final FeatureFlagConfigStore featureFlagConfigStore;

	private final ProjectLookup projectLookup;

	private final EnvironmentLookup environmentLookup;

	FeatureFlagLookup(FeatureFlagStore featureFlagStore, FeatureFlagConfigStore featureFlagConfigStore,
			ProjectLookup projectLookup, EnvironmentLookup environmentLookup) {
		this.featureFlagStore = featureFlagStore;
		this.featureFlagConfigStore = featureFlagConfigStore;
		this.projectLookup = projectLookup;
		this.environmentLookup = environmentLookup;
	}

	ProjectRef resolveProject(String projectKey) {
		return projectLookup.findByKey(projectKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "PROJECT_NOT_FOUND", "No project with that key."));
	}

	FeatureFlag resolveFlag(ProjectRef project, String flagKey) {
		return featureFlagStore.findByProjectIdAndKey(project.id(), flagKey).orElseThrow(
				() -> new ApiException(NOT_FOUND, "FLAG_NOT_FOUND", "No flag with that key in this project."));
	}

	EnvironmentRef resolveEnvironment(ProjectRef project, String environmentKey) {
		return environmentLookup.findByProjectIdAndKey(project.id(), environmentKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "ENVIRONMENT_NOT_FOUND",
						"No environment '" + environmentKey + "' in this project."));
	}

	FeatureFlagConfig resolveConfig(FeatureFlag flag, EnvironmentRef environment) {
		return featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id())
				.orElseThrow(() -> new ApiException(NOT_FOUND, "CONFIG_NOT_FOUND",
						"No configuration for this flag in this environment."));
	}

	FeatureFlagView viewOf(FeatureFlag flag, ProjectRef project) {
		return new FeatureFlagView(flag, featureFlagConfigStore.findByFeatureFlagId(flag.getId()),
				environmentLookup.findByProjectId(project.id()));
	}

	List<EnvironmentRef> environmentsOf(ProjectRef project) {
		return environmentLookup.findByProjectId(project.id());
	}
}
