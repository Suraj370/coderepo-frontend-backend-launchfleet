package com.launchfleet.backend.featureflags.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.TargetingRule;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Adds one new targeting rule to a flag's config in one environment. Loads the
 * current rule list, appends the new rule, and calls FeatureFlagConfig.updateTargetingRules
 * exactly once with the full resulting list - never mutating the list in place and
 * saving per-rule (see updateTargetingRules' Javadoc on why version bumps once).
 */
@Component
public class AddTargetingRule {

	private static final int BAD_REQUEST = 400;

	private final FeatureFlagConfigStore featureFlagConfigStore;

	private final FeatureFlagLookup lookup;

	private final TargetingRuleValidator validator;

	AddTargetingRule(FeatureFlagConfigStore featureFlagConfigStore, FeatureFlagLookup lookup,
			TargetingRuleValidator validator) {
		this.featureFlagConfigStore = featureFlagConfigStore;
		this.lookup = lookup;
		this.validator = validator;
	}

	public FeatureFlagView execute(String projectKey, String flagKey, String environmentKey, int priority,
			List<Condition> conditions, String variantId, String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);
		FeatureFlag flag = lookup.resolveFlag(project, flagKey);
		EnvironmentRef environment = lookup.resolveEnvironment(project, environmentKey);
		FeatureFlagConfig config = lookup.resolveConfig(flag, environment);

		validator.validateVariantBelongsToFlag(flag, variantId);
		validator.validateSegmentReferences(project, conditions);

		TargetingRule newRule;
		try {
			newRule = TargetingRule.create(priority, conditions, variantId);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}

		List<TargetingRule> rules = new ArrayList<>(config.getTargetingRules());
		rules.add(newRule);

		try {
			config.updateTargetingRules(rules, actingUserId);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}

		featureFlagConfigStore.save(config);

		return lookup.viewOf(flag, project);
	}
}
