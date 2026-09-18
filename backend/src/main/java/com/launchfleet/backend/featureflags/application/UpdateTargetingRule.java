package com.launchfleet.backend.featureflags.application;

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

@Component
public class UpdateTargetingRule {

	private static final int NOT_FOUND = 404;

	private static final int BAD_REQUEST = 400;

	private final FeatureFlagConfigStore featureFlagConfigStore;

	private final FeatureFlagLookup lookup;

	private final TargetingRuleValidator validator;

	UpdateTargetingRule(FeatureFlagConfigStore featureFlagConfigStore, FeatureFlagLookup lookup,
			TargetingRuleValidator validator) {
		this.featureFlagConfigStore = featureFlagConfigStore;
		this.lookup = lookup;
		this.validator = validator;
	}

	public FeatureFlagView execute(String projectKey, String flagKey, String environmentKey, String ruleId,
			int priority, List<Condition> conditions, String variantId, String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);
		FeatureFlag flag = lookup.resolveFlag(project, flagKey);
		EnvironmentRef environment = lookup.resolveEnvironment(project, environmentKey);
		FeatureFlagConfig config = lookup.resolveConfig(flag, environment);

		validator.validateVariantBelongsToFlag(flag, variantId);
		validator.validateSegmentReferences(project, conditions);

		boolean found = config.getTargetingRules().stream().anyMatch(rule -> rule.getId().equals(ruleId));
		if (!found) {
			throw new ApiException(NOT_FOUND, "TARGETING_RULE_NOT_FOUND", "No targeting rule with that id.");
		}

		List<TargetingRule> rules;
		try {
			rules = config.getTargetingRules().stream()
					.map(rule -> rule.getId().equals(ruleId) ? rule.withUpdatedFields(priority, conditions, variantId)
							: rule)
					.toList();
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}

		try {
			config.updateTargetingRules(rules, actingUserId);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}

		featureFlagConfigStore.save(config);

		return lookup.viewOf(flag, project);
	}
}
