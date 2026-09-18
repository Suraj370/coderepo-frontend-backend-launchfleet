package com.launchfleet.backend.featureflags.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Mutates one flag's configuration in one environment - enabled state, default
 * variant, or both in a single call. Each field that's actually present in the
 * request triggers its own domain-level version bump (see FeatureFlagConfig), so a
 * request changing both increments the version twice - one increment per distinct
 * change, matching the existing single-field versioning convention rather than
 * inventing a combined-update rule.
 */
@Component
public class UpdateEnvironmentConfig {

	private static final int NOT_FOUND = 404;

	private static final int BAD_REQUEST = 400;

	private final FeatureFlagConfigStore featureFlagConfigStore;

	private final FeatureFlagLookup lookup;

	UpdateEnvironmentConfig(FeatureFlagConfigStore featureFlagConfigStore, FeatureFlagLookup lookup) {
		this.featureFlagConfigStore = featureFlagConfigStore;
		this.lookup = lookup;
	}

	public FeatureFlagView execute(String projectKey, String flagKey, String environmentKey, Boolean enabled,
			String defaultVariantId, String actingUserId) {
		if (enabled == null && defaultVariantId == null) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR",
					"Provide at least one of enabled or defaultVariantId.");
		}

		ProjectRef project = lookup.resolveProject(projectKey);
		FeatureFlag flag = lookup.resolveFlag(project, flagKey);
		EnvironmentRef environment = lookup.resolveEnvironment(project, environmentKey);
		FeatureFlagConfig config = featureFlagConfigStore
				.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id())
				.orElseThrow(() -> new ApiException(NOT_FOUND, "CONFIG_NOT_FOUND",
						"No configuration for this flag in this environment."));

		if (enabled != null) {
			config.updateEnabled(enabled, actingUserId);
		}

		if (defaultVariantId != null) {
			boolean belongsToFlag = flag.getVariants().stream().anyMatch(variant -> variant.id().equals(defaultVariantId));

			if (!belongsToFlag) {
				throw new ApiException(BAD_REQUEST, "INVALID_VARIANT", "That variant does not belong to this flag.");
			}

			config.updateDefaultVariant(defaultVariantId, actingUserId);
		}

		featureFlagConfigStore.save(config);

		return lookup.viewOf(flag, project);
	}
}
