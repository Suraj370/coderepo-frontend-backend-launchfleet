package com.launchfleet.backend.featureflags.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.TargetingRule;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

@Component
public class RemoveTargetingRule {

	private static final int NOT_FOUND = 404;

	private final FeatureFlagConfigStore featureFlagConfigStore;

	private final FeatureFlagLookup lookup;

	RemoveTargetingRule(FeatureFlagConfigStore featureFlagConfigStore, FeatureFlagLookup lookup) {
		this.featureFlagConfigStore = featureFlagConfigStore;
		this.lookup = lookup;
	}

	public FeatureFlagView execute(String projectKey, String flagKey, String environmentKey, String ruleId,
			String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);
		FeatureFlag flag = lookup.resolveFlag(project, flagKey);
		EnvironmentRef environment = lookup.resolveEnvironment(project, environmentKey);
		FeatureFlagConfig config = lookup.resolveConfig(flag, environment);

		boolean found = config.getTargetingRules().stream().anyMatch(rule -> rule.getId().equals(ruleId));
		if (!found) {
			throw new ApiException(NOT_FOUND, "TARGETING_RULE_NOT_FOUND", "No targeting rule with that id.");
		}

		List<TargetingRule> rules = config.getTargetingRules().stream().filter(rule -> !rule.getId().equals(ruleId))
				.toList();

		config.updateTargetingRules(rules, actingUserId);
		featureFlagConfigStore.save(config);

		return lookup.viewOf(flag, project);
	}
}
