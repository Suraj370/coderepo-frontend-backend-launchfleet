package com.launchfleet.backend.featureflags.application;

import java.time.Instant;
import java.util.List;

import com.launchfleet.backend.featureflags.domain.Segment;

/**
 * The complete, already-assembled result of GetSdkConfiguration for one environment -
 * everything an SDK needs to evaluate locally, joined and scoped, but still shaped
 * with domain types (Segment, and SdkFlagView wrapping TargetingRule/Rollout/Variant)
 * rather than the wire format. The web adapter maps this to SdkConfigurationResponse;
 * this type never touches HTTP/JSON concerns itself.
 *
 * version is the read-time aggregate described in GetSdkConfiguration - a hash of every
 * included flag config's version plus every included (referenced) segment's updatedAt,
 * so it changes exactly when the payload's own content would change.
 */
public record SdkConfigurationResult(String environmentId, String environmentKey, String version, Instant generatedAt,
		List<SdkFlagView> flags, List<Segment> segments) {
}
