package com.launchfleet.sdk.transport;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mirrors the backend's SdkConditionResponse. type/operator are plain strings, parsed against the SDK's own enums in configuration/. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SdkConditionWire(String type, String attribute, String operator, List<String> values) {
}
