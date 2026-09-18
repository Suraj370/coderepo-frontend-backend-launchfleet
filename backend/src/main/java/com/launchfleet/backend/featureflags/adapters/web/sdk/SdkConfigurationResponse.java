package com.launchfleet.backend.featureflags.adapters.web.sdk;

import java.time.Instant;
import java.util.List;

/**
 * The complete wire contract an SDK receives from GET /api/v1/sdk/config - explicit
 * DTOs, never FeatureFlagConfigDocument/SegmentDocument. version is the read-time
 * aggregate hash (see GetSdkConfiguration); the same value is also echoed as the
 * response's ETag for conditional GET.
 */
public record SdkConfigurationResponse(String environmentId, String environmentKey, String version,
		Instant generatedAt, List<SdkFlagResponse> flags, List<SdkSegmentResponse> segments) {
}
