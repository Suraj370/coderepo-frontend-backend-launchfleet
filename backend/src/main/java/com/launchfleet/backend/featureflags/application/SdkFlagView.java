package com.launchfleet.backend.featureflags.application;

import java.util.List;

import com.launchfleet.backend.featureflags.domain.Rollout;
import com.launchfleet.backend.featureflags.domain.TargetingRule;
import com.launchfleet.backend.featureflags.domain.Variant;

/**
 * One flag's SDK-facing view within one environment - a projection joining FeatureFlag
 * (key, variants) with that flag's FeatureFlagConfig in the requested environment
 * (enabled, defaultVariant, targetingRules, rollout, version). Assembled by
 * GetSdkConfiguration; shaped into the wire DTO by the web adapter, never serialized
 * directly.
 */
public record SdkFlagView(String key, boolean enabled, Variant defaultVariant, List<Variant> variants,
		List<TargetingRule> targetingRules, Rollout rollout, int version) {
}
