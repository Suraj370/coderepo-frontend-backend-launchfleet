package com.launchfleet.sdk.configuration;

import java.util.List;

/**
 * conditions are AND-combined, the same rule TargetingRule uses (see FlagEvaluator) -
 * the backend's Segment/Condition javadoc establishes "one coherent condition model"
 * without separately restating the combination rule for a segment's own conditions, so
 * this SDK applies the one already-documented rule uniformly rather than inventing a
 * second combination semantic.
 */
public record SnapshotSegment(String id, String key, List<SnapshotCondition> conditions) {
}
