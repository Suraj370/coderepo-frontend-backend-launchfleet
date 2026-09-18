package com.launchfleet.backend.featureflags.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class SegmentTest {

	private static final Condition ATTRIBUTE_CONDITION = new Condition(ConditionType.ATTRIBUTE, "plan",
			ConditionOperator.EQUALS, List.of("gold"));

	private static final Condition SEGMENT_MATCH_CONDITION = new Condition(ConditionType.SEGMENT_MATCH, null,
			ConditionOperator.EQUALS, List.of("segment-1"));

	@Test
	void createStartsActive() {
		Segment segment = Segment.create("project-1", "beta-users", "Beta Users", List.of(ATTRIBUTE_CONDITION),
				"actor");

		assertThat(segment.getStatus()).isEqualTo(SegmentStatus.ACTIVE);
		assertThat(segment.getProjectId()).isEqualTo("project-1");
		assertThat(segment.getKey()).isEqualTo("beta-users");
	}

	@Test
	void createRejectsZeroConditions() {
		assertThatThrownBy(() -> Segment.create("project-1", "beta-users", "Beta Users", List.of(), "actor"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void createRejectsSegmentMatchConditionsPreventingCycles() {
		assertThatThrownBy(() -> Segment.create("project-1", "beta-users", "Beta Users",
				List.of(SEGMENT_MATCH_CONDITION), "actor")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void updateRejectsSegmentMatchConditions() {
		Segment segment = Segment.create("project-1", "beta-users", "Beta Users", List.of(ATTRIBUTE_CONDITION),
				"actor");

		assertThatThrownBy(() -> segment.update("Beta Users", List.of(SEGMENT_MATCH_CONDITION), "actor"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void retireTransitionsToRetired() {
		Segment segment = Segment.create("project-1", "beta-users", "Beta Users", List.of(ATTRIBUTE_CONDITION),
				"actor");

		segment.retire("actor");

		assertThat(segment.getStatus()).isEqualTo(SegmentStatus.RETIRED);
	}

	@Test
	void retiringAnAlreadyRetiredSegmentFails() {
		Segment segment = Segment.create("project-1", "beta-users", "Beta Users", List.of(ATTRIBUTE_CONDITION),
				"actor");
		segment.retire("actor");

		assertThatThrownBy(() -> segment.retire("actor")).isInstanceOf(IllegalStateException.class);
	}
}
