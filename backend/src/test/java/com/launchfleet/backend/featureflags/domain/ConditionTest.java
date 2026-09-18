package com.launchfleet.backend.featureflags.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConditionTest {

	@Test
	void attributeConditionRequiresAnAttributeName() {
		assertThatThrownBy(() -> new Condition(ConditionType.ATTRIBUTE, null, ConditionOperator.EQUALS, List.of("x")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void userKeyConditionRejectsAnAttributeName() {
		assertThatThrownBy(
				() -> new Condition(ConditionType.USER_KEY, "plan", ConditionOperator.EQUALS, List.of("alice")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void equalsRequiresExactlyOneValue() {
		assertThatThrownBy(() -> new Condition(ConditionType.ATTRIBUTE, "plan", ConditionOperator.EQUALS,
				List.of("gold", "silver"))).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(
				() -> new Condition(ConditionType.ATTRIBUTE, "plan", ConditionOperator.EQUALS, List.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void inRequiresAtLeastOneValue() {
		assertThatThrownBy(() -> new Condition(ConditionType.ATTRIBUTE, "plan", ConditionOperator.IN, List.of()))
				.isInstanceOf(IllegalArgumentException.class);

		Condition condition = new Condition(ConditionType.ATTRIBUTE, "plan", ConditionOperator.IN,
				List.of("gold", "silver"));
		assertThat(condition.values()).containsExactly("gold", "silver");
	}

	@Test
	void segmentMatchMustUseEquals() {
		assertThatThrownBy(
				() -> new Condition(ConditionType.SEGMENT_MATCH, null, ConditionOperator.IN, List.of("segment-1")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aValidAttributeConditionIsConstructed() {
		Condition condition = new Condition(ConditionType.ATTRIBUTE, "plan", ConditionOperator.EQUALS,
				List.of("gold"));

		assertThat(condition.type()).isEqualTo(ConditionType.ATTRIBUTE);
		assertThat(condition.attribute()).isEqualTo("plan");
		assertThat(condition.values()).containsExactly("gold");
	}
}
