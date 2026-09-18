package com.launchfleet.sdk.configuration;

import java.util.List;

/**
 * type/attribute/operator/values mean exactly what they mean server-side (see the
 * backend's Condition javadoc): for SEGMENT_MATCH, values holds exactly one segment id
 * (resolved against ConfigurationSnapshot.segment(id)), always compared with EQUALS.
 */
public record SnapshotCondition(ConditionType type, String attribute, ConditionOperator operator,
		List<String> values) {
}
