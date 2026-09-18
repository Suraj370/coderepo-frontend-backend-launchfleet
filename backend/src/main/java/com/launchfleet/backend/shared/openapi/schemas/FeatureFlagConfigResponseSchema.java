package com.launchfleet.backend.shared.openapi.schemas;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI documentation only - mirrors FeatureFlagResource.toMap(FeatureFlagConfig, String). */
@Schema(description = "How one flag behaves in one environment.")
public record FeatureFlagConfigResponseSchema(

		String id,

		String featureFlagId,

		String environmentId,

		boolean enabled,

		String defaultVariantId,

		List<TargetingRuleSchema> targetingRules,

		@Schema(nullable = true, description = "Null when no rollout is configured - defaultVariantId is served instead.") RolloutSchema rollout,

		int version,

		String updatedBy,

		Instant updatedAt,

		Instant createdAt,

		String environmentKey) {
}
