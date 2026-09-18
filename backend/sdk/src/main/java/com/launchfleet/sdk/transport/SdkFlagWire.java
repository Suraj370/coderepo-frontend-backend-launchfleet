package com.launchfleet.sdk.transport;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SdkFlagWire(String key, boolean enabled, SdkVariantWire defaultVariant, List<SdkVariantWire> variants,
		List<SdkTargetingRuleWire> targetingRules, SdkRolloutWire rollout, int version) {
}
