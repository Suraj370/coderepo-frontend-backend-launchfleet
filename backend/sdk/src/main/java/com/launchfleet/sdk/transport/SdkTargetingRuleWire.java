package com.launchfleet.sdk.transport;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SdkTargetingRuleWire(String id, int priority, List<SdkConditionWire> conditions, String variantId) {
}
