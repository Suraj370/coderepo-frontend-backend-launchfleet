package com.launchfleet.sdk.transport;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SdkSegmentWire(String id, String key, List<SdkConditionWire> conditions) {
}
