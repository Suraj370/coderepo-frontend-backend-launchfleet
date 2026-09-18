package com.launchfleet.backend.featureflags.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.ports.ProjectRef;

@Component
public class GetFeatureFlag {

	private final FeatureFlagLookup lookup;

	GetFeatureFlag(FeatureFlagLookup lookup) {
		this.lookup = lookup;
	}

	public FeatureFlagView execute(String projectKey, String flagKey) {
		ProjectRef project = lookup.resolveProject(projectKey);
		FeatureFlag flag = lookup.resolveFlag(project, flagKey);

		return lookup.viewOf(flag, project);
	}
}
