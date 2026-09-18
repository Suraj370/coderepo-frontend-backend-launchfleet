package com.launchfleet.backend.featureflags.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;

/** Clears a flag's config rollout in one environment, reverting to plain defaultVariantId fallback behavior. */
@Component
public class RemoveRollout {

	private final FeatureFlagConfigStore featureFlagConfigStore;

	private final FeatureFlagLookup lookup;

	RemoveRollout(FeatureFlagConfigStore featureFlagConfigStore, FeatureFlagLookup lookup) {
		this.featureFlagConfigStore = featureFlagConfigStore;
		this.lookup = lookup;
	}

	public FeatureFlagView execute(String projectKey, String flagKey, String environmentKey, String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);
		FeatureFlag flag = lookup.resolveFlag(project, flagKey);
		EnvironmentRef environment = lookup.resolveEnvironment(project, environmentKey);
		FeatureFlagConfig config = lookup.resolveConfig(flag, environment);

		config.removeRollout(actingUserId);
		featureFlagConfigStore.save(config);

		return lookup.viewOf(flag, project);
	}
}
