package com.launchfleet.sdk.configuration;

import java.util.List;

/** Conditions are AND-combined (see FlagEvaluator) - matches the backend TargetingRule contract exactly. */
public record SnapshotTargetingRule(String id, int priority, List<SnapshotCondition> conditions, String variantId) {
}
