package com.launchfleet.sdk.transport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SdkAllocationWire(String variantId, int percentage) {
}
