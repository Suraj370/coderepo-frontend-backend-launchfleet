package com.launchfleet.backend.featureflags.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the locked evaluation order (disabled -> targeting rules
 * -> rollout -> default) as a composition of already-existing, independently tested
 * pieces: FeatureFlagConfig.isEnabled/getDefaultVariantId/getTargetingRules/getRollout
 * and RolloutAssigner.assign. Deliberately does NOT introduce a general evaluation
 * engine or endpoint - "matches" here is the simplest possible inline simulation of a
 * single USER_KEY/EQUALS rule, just enough to prove the ORDER composes correctly, not
 * a reusable matcher. A real evaluator (Phase 5+) would replace this inlined snippet
 * with full condition/segment matching while keeping the same order.
 */
class RolloutEvaluationOrderTest {

	private static final String ENVIRONMENT_ID = "env-1";

	private static final String FLAG_KEY = "checkout";

	/** Simulates "does the first matching rule's variant win", the one piece this phase does not implement. */
	private static Optional<String> firstMatchingRuleVariant(List<TargetingRule> rules, String userKey) {
		return rules.stream()
				.filter(rule -> rule.getConditions().stream().allMatch(condition -> condition.type() == ConditionType.USER_KEY
						&& condition.values().contains(userKey)))
				.findFirst().map(TargetingRule::getVariantId);
	}

	/** The locked flow, composed purely from existing, independently tested pieces. */
	private static String evaluate(FeatureFlagConfig config, String userKey) {
		if (!config.isEnabled()) {
			return config.getDefaultVariantId();
		}

		Optional<String> ruleMatch = firstMatchingRuleVariant(config.getTargetingRules(), userKey);
		if (ruleMatch.isPresent()) {
			return ruleMatch.get();
		}

		if (config.getRollout() != null) {
			Optional<String> rolloutMatch = RolloutAssigner.assign(ENVIRONMENT_ID, FLAG_KEY, userKey,
					config.getRollout());
			if (rolloutMatch.isPresent()) {
				return rolloutMatch.get();
			}
		}

		return config.getDefaultVariantId();
	}

	@Test
	void aMatchingTargetingRuleTakesPrecedenceOverRollout() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", ENVIRONMENT_ID, "project-1",
				"default-variant", "actor");
		config.updateEnabled(true, "actor");
		config.updateTargetingRules(List.of(TargetingRule.create(1,
				List.of(new Condition(ConditionType.USER_KEY, null, ConditionOperator.EQUALS, List.of("alice"))),
				"rule-variant")), "actor");
		config.updateRollout(
				new Rollout(List.of(new Allocation("rollout-variant-a", 5000), new Allocation("rollout-variant-b", 5000))),
				"actor");

		assertThat(evaluate(config, "alice")).isEqualTo("rule-variant");
	}

	@Test
	void aUserNotMatchedByAnyRuleFallsThroughToRollout() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", ENVIRONMENT_ID, "project-1",
				"default-variant", "actor");
		config.updateEnabled(true, "actor");
		config.updateTargetingRules(List.of(TargetingRule.create(1,
				List.of(new Condition(ConditionType.USER_KEY, null, ConditionOperator.EQUALS, List.of("alice"))),
				"rule-variant")), "actor");
		config.updateRollout(new Rollout(List.of(new Allocation("rollout-only-variant", 10000))), "actor");

		assertThat(evaluate(config, "bob")).isEqualTo("rollout-only-variant");
	}

	@Test
	void noRolloutConfiguredFallsThroughToDefaultVariant() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", ENVIRONMENT_ID, "project-1",
				"default-variant", "actor");
		config.updateEnabled(true, "actor");

		assertThat(evaluate(config, "bob")).isEqualTo("default-variant");
	}

	@Test
	void rolloutConfiguredButUserKeyMissingFallsThroughToDefaultVariant() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", ENVIRONMENT_ID, "project-1",
				"default-variant", "actor");
		config.updateEnabled(true, "actor");
		config.updateRollout(new Rollout(List.of(new Allocation("rollout-only-variant", 10000))), "actor");

		assertThat(evaluate(config, null)).isEqualTo("default-variant");
		assertThat(evaluate(config, "  ")).isEqualTo("default-variant");
	}

	@Test
	void disabledConfigurationSkipsBothTargetingAndRolloutAndServesDefaultVariant() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", ENVIRONMENT_ID, "project-1",
				"default-variant", "actor");
		config.updateTargetingRules(List.of(TargetingRule.create(1,
				List.of(new Condition(ConditionType.USER_KEY, null, ConditionOperator.EQUALS, List.of("alice"))),
				"rule-variant")), "actor");
		config.updateRollout(new Rollout(List.of(new Allocation("rollout-only-variant", 10000))), "actor");
		// Config remains disabled (createDisabled's default) despite having rules and a rollout configured.

		assertThat(evaluate(config, "alice")).isEqualTo("default-variant");
	}
}
