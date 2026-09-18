package com.launchfleet.backend.featureflags.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Pure unit tests of FeatureFlagConfig's own invariants - no Spring, no MongoDB. */
class FeatureFlagConfigTest {

	@Test
	void createDisabledStartsDisabledAtVersionOne() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"actor");

		assertThat(config.isEnabled()).isFalse();
		assertThat(config.getVersion()).isEqualTo(1);
		assertThat(config.getUpdatedBy()).isEqualTo("actor");
	}

	@Test
	void updateEnabledFlipsStateAndBumpsVersion() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"creator");

		config.updateEnabled(true, "editor");

		assertThat(config.isEnabled()).isTrue();
		assertThat(config.getVersion()).isEqualTo(2);
		assertThat(config.getUpdatedBy()).isEqualTo("editor");
	}

	@Test
	void eachUpdateBumpsVersionAgain() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"actor");

		config.updateEnabled(true, "actor");
		config.updateEnabled(false, "actor");
		config.updateEnabled(true, "actor");

		assertThat(config.getVersion()).isEqualTo(4);
	}

	@Test
	void updateDefaultVariantChangesTheVariantAndBumpsVersion() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"creator");

		config.updateDefaultVariant("variant-2", "editor");

		assertThat(config.getDefaultVariantId()).isEqualTo("variant-2");
		assertThat(config.getVersion()).isEqualTo(2);
		assertThat(config.getUpdatedBy()).isEqualTo("editor");
	}

	@Test
	void updateDefaultVariantRejectsABlankId() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"actor");

		assertThatThrownBy(() -> config.updateDefaultVariant(" ", "actor"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void enablingAndChangingVariantEachBumpVersionIndependently() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"actor");

		config.updateEnabled(true, "actor");
		config.updateDefaultVariant("variant-2", "actor");

		assertThat(config.getVersion()).isEqualTo(3);
	}

	@Test
	void createDisabledStartsWithNoTargetingRules() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"actor");

		assertThat(config.getTargetingRules()).isEmpty();
	}

	@Test
	void updateTargetingRulesReplacesTheListAndBumpsVersionOnce() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"actor");

		TargetingRule rule1 = TargetingRule.create(1, List.of(namedUserCondition("alice")), "variant-1");
		TargetingRule rule2 = TargetingRule.create(2, List.of(namedUserCondition("bob")), "variant-2");

		config.updateTargetingRules(List.of(rule1, rule2), "editor");

		assertThat(config.getTargetingRules()).hasSize(2);
		assertThat(config.getVersion()).isEqualTo(2);
		assertThat(config.getUpdatedBy()).isEqualTo("editor");
	}

	@Test
	void updateTargetingRulesOrdersByPriorityRegardlessOfInputOrder() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"actor");

		TargetingRule low = TargetingRule.create(5, List.of(namedUserCondition("alice")), "variant-1");
		TargetingRule high = TargetingRule.create(1, List.of(namedUserCondition("bob")), "variant-2");

		config.updateTargetingRules(List.of(low, high), "actor");

		assertThat(config.getTargetingRules()).extracting(TargetingRule::getPriority).containsExactly(1, 5);
	}

	@Test
	void updateTargetingRulesRejectsDuplicateRuleIds() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"actor");

		TargetingRule rule = TargetingRule.create(1, List.of(namedUserCondition("alice")), "variant-1");
		TargetingRule duplicate = TargetingRule.reconstitute(rule.getId(), 2, List.of(namedUserCondition("bob")),
				"variant-2");

		assertThatThrownBy(() -> config.updateTargetingRules(List.of(rule, duplicate), "actor"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void repeatedTargetingRuleUpdatesEachBumpVersionOnce() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"actor");

		config.updateTargetingRules(List.of(TargetingRule.create(1, List.of(namedUserCondition("alice")), "variant-1")),
				"actor");
		config.updateTargetingRules(List.of(TargetingRule.create(1, List.of(namedUserCondition("bob")), "variant-1")),
				"actor");

		assertThat(config.getVersion()).isEqualTo(3);
	}

	@Test
	void createDisabledStartsWithNoRollout() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"actor");

		assertThat(config.getRollout()).isNull();
	}

	@Test
	void updateRolloutSetsItAndBumpsVersionOnce() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"actor");

		Rollout rollout = new Rollout(List.of(new Allocation("variant-1", 4000), new Allocation("variant-2", 6000)));
		config.updateRollout(rollout, "editor");

		assertThat(config.getRollout()).isEqualTo(rollout);
		assertThat(config.getVersion()).isEqualTo(2);
		assertThat(config.getUpdatedBy()).isEqualTo("editor");
	}

	@Test
	void repeatedRolloutUpdatesEachBumpVersionOnceRegardlessOfAllocationCount() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"actor");

		config.updateRollout(new Rollout(List.of(new Allocation("variant-1", 10000))), "actor");
		config.updateRollout(new Rollout(List.of(new Allocation("variant-1", 3000), new Allocation("variant-2", 3000),
				new Allocation("variant-3", 4000))), "actor");

		assertThat(config.getVersion()).isEqualTo(3);
	}

	@Test
	void removeRolloutClearsItAndBumpsVersionOnce() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"actor");
		config.updateRollout(new Rollout(List.of(new Allocation("variant-1", 10000))), "actor");

		config.removeRollout("editor");

		assertThat(config.getRollout()).isNull();
		assertThat(config.getVersion()).isEqualTo(3);
		assertThat(config.getUpdatedBy()).isEqualTo("editor");
	}

	@Test
	void rolloutAndTargetingRuleVersionBumpsAreIndependent() {
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled("flag-1", "env-1", "project-1", "variant-1",
				"actor");

		config.updateTargetingRules(List.of(TargetingRule.create(1, List.of(namedUserCondition("alice")), "variant-1")),
				"actor");
		config.updateRollout(new Rollout(List.of(new Allocation("variant-1", 10000))), "actor");

		assertThat(config.getVersion()).isEqualTo(3);
	}

	private static Condition namedUserCondition(String userKey) {
		return new Condition(ConditionType.USER_KEY, null, ConditionOperator.EQUALS, List.of(userKey));
	}
}
