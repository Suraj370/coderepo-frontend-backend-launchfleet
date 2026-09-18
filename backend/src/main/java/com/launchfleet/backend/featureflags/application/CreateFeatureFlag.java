package com.launchfleet.backend.featureflags.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.activity.ActivityAction;
import com.launchfleet.backend.activity.ActivityRecorder;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.FlagType;
import com.launchfleet.backend.featureflags.domain.Variant;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Creates a flag and, as one operation, a disabled FeatureFlagConfig for every
 * environment that already exists in the project - a flag never has an undefined
 * config state for an existing environment.
 */
@Component
public class CreateFeatureFlag {

	private static final int CONFLICT = 409;

	private final FeatureFlagStore featureFlagStore;

	private final FeatureFlagConfigStore featureFlagConfigStore;

	private final FeatureFlagLookup lookup;

	private final ActivityRecorder activityLogService;

	CreateFeatureFlag(FeatureFlagStore featureFlagStore, FeatureFlagConfigStore featureFlagConfigStore,
			FeatureFlagLookup lookup, ActivityRecorder activityLogService) {
		this.featureFlagStore = featureFlagStore;
		this.featureFlagConfigStore = featureFlagConfigStore;
		this.lookup = lookup;
		this.activityLogService = activityLogService;
	}

	public FeatureFlagView execute(String projectKey, String key, String name, String description, FlagType type,
			List<Variant> requestedVariants, String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);

		if (featureFlagStore.existsByProjectIdAndKey(project.id(), key)) {
			throw new ApiException(CONFLICT, "FLAG_KEY_TAKEN", "A flag with this key already exists in this project.");
		}

		FeatureFlag flag = FeatureFlag.create(project.id(), key, name, description, type, requestedVariants,
				actingUserId);
		FeatureFlag saved = featureFlagStore.save(flag);

		List<EnvironmentRef> environments = lookup.environmentsOf(project);
		String defaultVariantId = saved.defaultVariant().id();
		List<FeatureFlagConfig> configs = new ArrayList<>();

		for (EnvironmentRef environment : environments) {
			FeatureFlagConfig config = FeatureFlagConfig.createDisabled(saved.getId(), environment.id(),
					project.id(), defaultVariantId, actingUserId);
			configs.add(featureFlagConfigStore.save(config));
		}

		activityLogService.record(project.id(), actingUserId, ActivityAction.FLAG_CREATED, "flag", saved.getKey(),
				null);

		return new FeatureFlagView(saved, configs, environments);
	}
}
