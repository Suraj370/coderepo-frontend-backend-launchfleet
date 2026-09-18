package com.launchfleet.backend.experiments.application;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.domain.SegmentStatus;
import com.launchfleet.backend.featureflags.domain.TargetingRule;
import com.launchfleet.backend.featureflags.ports.SegmentStore;

/**
 * A narrow port of the targeting-rule matching order established by
 * sdk.evaluation.FlagEvaluator onto the backend's own
 * FeatureFlagConfig/TargetingRule/Segment/Condition domain types. Written here
 * rather than calling FlagEvaluator directly because the sdk module is a
 * deliberately independent, publishable client library with zero dependency on
 * the backend (see backend/settings.gradle) and vice versa; adding a
 * backend -> sdk Gradle dependency to reach it would cross that boundary to fix
 * an experiments bug. This is a port of the same algorithm/order onto a
 * structurally identical set of types, not a second, divergent evaluator.
 *
 * Only attribute-less matching is possible here: the SDK's assignment/event HTTP
 * requests carry a userKey only (see CreateExperimentAssignmentRequest) - the
 * platform never receives arbitrary user attributes over the wire in this
 * increment (local evaluation keeps attributes entirely client-side). An
 * ATTRIBUTE condition therefore behaves exactly like FlagEvaluator.matchesAttribute
 * given a null/absent value: it never matches. USER_KEY and SEGMENT_MATCH
 * conditions (whose own segment conditions are, in turn, ATTRIBUTE/USER_KEY) are
 * unaffected and evaluate normally.
 *
 * Eligibility policy for NEW experiment enrollment only (an existing persisted
 * assignment is never re-checked against this - see CreateExperimentAssignment):
 *  - a config with NO targeting rules gates nothing, so every user is eligible;
 *  - a config WITH targeting rules requires at least one rule (checked in the
 *    config's own stored priority order) to match; unmatched users are exactly
 *    the population the flag's targeting excludes.
 */
@Component
class FlagTargetingEligibility {

	private final SegmentStore segmentStore;

	FlagTargetingEligibility(SegmentStore segmentStore) {
		this.segmentStore = segmentStore;
	}

	boolean isEligible(FeatureFlagConfig config, String userKey) {
		List<TargetingRule> rules = config.getTargetingRules();
		if (rules.isEmpty()) {
			return true;
		}

		for (TargetingRule rule : rules) {
			if (matchesAll(rule.getConditions(), userKey)) {
				return true;
			}
		}

		return false;
	}

	private boolean matchesAll(List<Condition> conditions, String userKey) {
		for (Condition condition : conditions) {
			if (!matches(condition, userKey)) {
				return false;
			}
		}

		return true;
	}

	private boolean matches(Condition condition, String userKey) {
		return switch (condition.type()) {
			case ATTRIBUTE -> false;
			case USER_KEY -> matchesValue(userKey, condition);
			case SEGMENT_MATCH -> matchesSegment(condition, userKey);
		};
	}

	/** A retired or missing segment fails closed, exactly like FlagEvaluator.matchesSegment. */
	private boolean matchesSegment(Condition condition, String userKey) {
		String segmentId = condition.values().get(0);
		Optional<Segment> segment = segmentStore.findById(segmentId);
		if (segment.isEmpty() || segment.get().getStatus() == SegmentStatus.RETIRED) {
			return false;
		}

		return matchesAll(segment.get().getConditions(), userKey);
	}

	private boolean matchesValue(String actual, Condition condition) {
		if (actual == null || actual.isBlank()) {
			return false;
		}

		return switch (condition.operator()) {
			case EQUALS -> actual.equals(condition.values().get(0));
			case IN -> condition.values().contains(actual);
		};
	}
}
