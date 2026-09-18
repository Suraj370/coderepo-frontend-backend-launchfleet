package com.launchfleet.backend.featureflags.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/** Metadata only (name/description) - key/type/variants are immutable after creation. */
@Component
public class UpdateFeatureFlag {

	private static final int BAD_REQUEST = 400;

	private final FeatureFlagStore featureFlagStore;

	private final FeatureFlagLookup lookup;

	UpdateFeatureFlag(FeatureFlagStore featureFlagStore, FeatureFlagLookup lookup) {
		this.featureFlagStore = featureFlagStore;
		this.lookup = lookup;
	}

	public FeatureFlagView execute(String projectKey, String flagKey, String name, String description,
			String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);
		FeatureFlag flag = lookup.resolveFlag(project, flagKey);

		try {
			flag.updateMetadata(name, description, actingUserId);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}

		FeatureFlag saved = featureFlagStore.save(flag);

		return lookup.viewOf(saved, project);
	}
}
