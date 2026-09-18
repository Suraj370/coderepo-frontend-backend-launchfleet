package com.launchfleet.sdk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EvaluationContextTest {

	@Test
	void attributesMapIsImmutable() {
		EvaluationContext context = EvaluationContext.of("alice", Map.of("plan", "gold"));

		assertThatThrownBy(() -> context.attributes().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void mutatingTheCallerSuppliedMapAfterConstructionDoesNotAffectTheContext() {
		Map<String, Object> source = new HashMap<>();
		source.put("plan", "gold");
		EvaluationContext context = EvaluationContext.of("alice", source);

		source.put("plan", "silver");

		assertThat(context.attribute("plan")).isEqualTo("gold");
	}

	@Test
	void aNullAttributeValueIsTreatedAsAbsent() {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put("plan", null);
		EvaluationContext context = EvaluationContext.of("alice", attributes);

		assertThat(context.attributes()).doesNotContainKey("plan");
		assertThat(context.attribute("plan")).isNull();
	}

	@Test
	void collectionAttributeValuesAreRejected() {
		assertThatThrownBy(() -> EvaluationContext.of("alice", Map.of("roles", List.of("admin", "editor"))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void mapAttributeValuesAreRejected() {
		assertThatThrownBy(() -> EvaluationContext.of("alice", Map.of("nested", Map.of("a", "b"))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void numericAndBooleanAttributeValuesAreAccepted() {
		EvaluationContext context = EvaluationContext.of("alice", Map.of("age", 42, "beta", true));

		assertThat(context.attribute("age")).isEqualTo(42);
		assertThat(context.attribute("beta")).isEqualTo(true);
	}

	@Test
	void contextWithoutAttributesHasAnEmptyAttributeMap() {
		EvaluationContext context = EvaluationContext.of("alice");

		assertThat(context.attributes()).isEmpty();
	}

	@Test
	void userKeyIsCarriedThroughVerbatimIncludingNull() {
		assertThat(EvaluationContext.of("alice").userKey()).isEqualTo("alice");
		assertThat(EvaluationContext.of(null).userKey()).isNull();
	}
}
