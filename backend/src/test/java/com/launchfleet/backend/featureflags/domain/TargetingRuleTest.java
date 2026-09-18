package com.launchfleet.backend.featureflags.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class TargetingRuleTest {

	private static final Condition A_CONDITION = new Condition(ConditionType.USER_KEY, null, ConditionOperator.EQUALS,
			List.of("alice"));

	@Test
	void createGeneratesAStableId() {
		TargetingRule rule = TargetingRule.create(1, List.of(A_CONDITION), "variant-1");

		assertThat(rule.getId()).isNotBlank();
		assertThat(rule.getPriority()).isEqualTo(1);
		assertThat(rule.getVariantId()).isEqualTo("variant-1");
	}

	@Test
	void createRejectsZeroConditions() {
		assertThatThrownBy(() -> TargetingRule.create(1, List.of(), "variant-1"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void createRejectsABlankVariantId() {
		assertThatThrownBy(() -> TargetingRule.create(1, List.of(A_CONDITION), " "))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void withUpdatedFieldsKeepsTheSameId() {
		TargetingRule rule = TargetingRule.create(1, List.of(A_CONDITION), "variant-1");

		TargetingRule updated = rule.withUpdatedFields(5, List.of(A_CONDITION), "variant-2");

		assertThat(updated.getId()).isEqualTo(rule.getId());
		assertThat(updated.getPriority()).isEqualTo(5);
		assertThat(updated.getVariantId()).isEqualTo("variant-2");
	}

	@Test
	void withUpdatedFieldsRejectsZeroConditions() {
		TargetingRule rule = TargetingRule.create(1, List.of(A_CONDITION), "variant-1");

		assertThatThrownBy(() -> rule.withUpdatedFields(1, List.of(), "variant-1"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
