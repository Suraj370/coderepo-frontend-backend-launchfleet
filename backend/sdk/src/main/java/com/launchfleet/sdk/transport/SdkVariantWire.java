package com.launchfleet.sdk.transport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mirrors the backend's SdkVariantResponse field-for-field. Deserialized by Jackson's record support. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SdkVariantWire(String id, String key, String name, Object value) {
}
