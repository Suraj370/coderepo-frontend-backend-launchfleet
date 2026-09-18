package com.launchfleet.backend.featureflags.application;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;

@Component
public class ListFeatureFlags {

	private final FeatureFlagStore featureFlagStore;

	private final FeatureFlagConfigStore featureFlagConfigStore;

	private final FeatureFlagLookup lookup;

	ListFeatureFlags(FeatureFlagStore featureFlagStore, FeatureFlagConfigStore featureFlagConfigStore,
			FeatureFlagLookup lookup) {
		this.featureFlagStore = featureFlagStore;
		this.featureFlagConfigStore = featureFlagConfigStore;
		this.lookup = lookup;
	}

	public List<FeatureFlagView> execute(String projectKey) {
		ProjectRef project = lookup.resolveProject(projectKey);
		List<FeatureFlag> flags = featureFlagStore.findByProjectId(project.id());
		List<EnvironmentRef> environments = lookup.environmentsOf(project);
		Map<String, List<FeatureFlagConfig>> configsByFlagId = featureFlagConfigStore.findByProjectId(project.id())
				.stream().collect(Collectors.groupingBy(FeatureFlagConfig::getFeatureFlagId));

		return flags.stream()
				.map(flag -> new FeatureFlagView(flag, configsByFlagId.getOrDefault(flag.getId(), List.of()),
						environments))
				.toList();
	}
}
