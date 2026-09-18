package com.launchfleet.backend.featureflags.domain;

import java.util.List;

/**
 * A single IF-clause within a TargetingRule or Segment - a pure value object, no
 * identity, reused as-is by both owners (one coherent condition model, per the
 * established architecture). `type` determines how `values` is interpreted:
 *
 *  - ATTRIBUTE: `attribute` is required; values are compared against
 *    EvaluationContext.attributes().get(attribute).
 *  - USER_KEY: `attribute` must be absent; values are compared against
 *    EvaluationContext.userKey().
 *  - SEGMENT_MATCH: `attribute` must be absent; values holds exactly one Segment's
 *    internal id (not its project-facing key) - always EQUALS, since "matches
 *    segment A OR segment B" is expressed as two separate rules (see TargetingRule),
 *    not as one condition with multiple segment ids.
 *
 * Validated in the compact constructor so an invalid Condition cannot be
 * constructed at all - EQUALS requires exactly one value, IN requires at least one.
 */
public record Condition(ConditionType type, String attribute, ConditionOperator operator, List<String> values) {

	public Condition {
		if (type == null) {
			throw new IllegalArgumentException("type is required.");
		}

		if (operator == null) {
			throw new IllegalArgumentException("operator is required.");
		}

		if (type == ConditionType.ATTRIBUTE) {
			if (attribute == null || attribute.isBlank()) {
				throw new IllegalArgumentException("attribute is required for ATTRIBUTE conditions.");
			}
		} else if (attribute != null) {
			throw new IllegalArgumentException("attribute must not be set for " + type + " conditions.");
		}

		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("values is required.");
		}

		if (operator == ConditionOperator.EQUALS && values.size() != 1) {
			throw new IllegalArgumentException("EQUALS requires exactly one value.");
		}

		if (type == ConditionType.SEGMENT_MATCH && operator != ConditionOperator.EQUALS) {
			throw new IllegalArgumentException("SEGMENT_MATCH conditions must use EQUALS.");
		}

		values = List.copyOf(values);
	}
}
