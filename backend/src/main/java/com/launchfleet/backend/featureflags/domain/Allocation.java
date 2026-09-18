package com.launchfleet.backend.featureflags.domain;

/**
 * One variant's share of a Rollout's percentage split, expressed in basis points
 * (0-10000, where 10000 = 100%) - integer only, no floating point, so summation and
 * validation stay exact and deterministic. Whether variantId actually belongs to the
 * owning FeatureFlag is NOT checked here - same cross-aggregate reasoning as
 * TargetingRule.variantId: this type has no reference to FeatureFlag's variant list,
 * so that check is an application-layer concern (see TargetingRuleValidator).
 */
public record Allocation(String variantId, int percentage) {

	public Allocation {
		if (variantId == null || variantId.isBlank()) {
			throw new IllegalArgumentException("variantId is required.");
		}

		if (percentage < 0 || percentage > 10000) {
			throw new IllegalArgumentException("percentage must be between 0 and 10000 basis points.");
		}
	}
}
