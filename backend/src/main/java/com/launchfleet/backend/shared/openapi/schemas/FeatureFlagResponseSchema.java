package com.launchfleet.backend.shared.openapi.schemas;

import java.time.Instant;
import java.util.List;

import com.launchfleet.backend.featureflags.domain.FlagStatus;
import com.launchfleet.backend.featureflags.domain.FlagType;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI documentation only - mirrors FeatureFlagResource.toMap(FeatureFlagView)/toMap(FeatureFlag). */
public record FeatureFlagResponseSchema(

		String id,

		String projectId,

		String key,

		String name,

		String description,

		FlagType type,

		FlagStatus status,

		@Schema(description = "BOOLEAN flags always have exactly true/false; MULTIVARIANT flags have 2+ custom variants.") List<VariantSchema> variants,

		String createdBy,

		Instant createdAt,

		String updatedBy,

		Instant updatedAt,

		@Schema(description = "This flag's per-environment configuration, one entry per environment in the project.") List<FeatureFlagConfigResponseSchema> environments) {
}
