package com.launchfleet.sdk.transport;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The root of GET /api/v1/sdk/config's response body, mirroring the backend's
 * SdkConfigurationResponse exactly. generatedAt is kept as a raw string (not parsed to
 * an Instant here) purely to avoid a second Jackson module dependency
 * (jackson-datatype-jsr310) for a field the SDK doesn't need to reason about -
 * ConfigurationSnapshot's own fetchedAt (local receipt time) is what drives staleness.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SdkConfigurationWire(String environmentId, String environmentKey, String version, String generatedAt,
		List<SdkFlagWire> flags, List<SdkSegmentWire> segments) {
}
