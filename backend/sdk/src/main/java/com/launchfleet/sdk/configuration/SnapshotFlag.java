package com.launchfleet.sdk.configuration;

import java.util.List;
import java.util.Optional;

/**
 * targetingRules is always stored pre-sorted by (priority, id) - the same tie-breaking
 * order FeatureFlagConfig.updateTargetingRules already establishes server-side - so
 * FlagEvaluator never has to trust (or re-derive) ordering from the wire payload.
 */
public record SnapshotFlag(String key, boolean enabled, SnapshotVariant defaultVariant, List<SnapshotVariant> variants,
		List<SnapshotTargetingRule> targetingRules, SnapshotRollout rollout) {

	/**
	 * Empty when a rule/rollout allocation references a variant id that isn't actually
	 * on this flag - malformed upstream data, not a caller error. FlagEvaluator treats
	 * that as "this particular match doesn't apply" and keeps going, rather than
	 * crashing the evaluation or the whole snapshot load.
	 */
	public Optional<SnapshotVariant> variantById(String variantId) {
		return variants.stream().filter(variant -> variant.id().equals(variantId)).findFirst();
	}
}
