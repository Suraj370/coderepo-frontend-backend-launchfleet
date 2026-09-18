package com.launchfleet.backend.shared.openapi.schemas;

import java.time.Instant;

import com.launchfleet.backend.experiments.domain.ExperimentStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI documentation only - mirrors ExperimentResource.toMap(Experiment). */
public record ExperimentResponseSchema(

		String id,

		String projectId,

		String environmentId,

		String featureFlagId,

		String key,

		String name,

		String description,

		@Schema(nullable = true, description = "Null until configured (DRAFT) - frozen once the experiment starts and never re-read from the flag's own rollout afterward.") RolloutSchema allocation,

		@Schema(nullable = true) String conversionEventName,

		ExperimentStatus status,

		int version,

		String createdBy,

		Instant createdAt,

		String updatedBy,

		Instant updatedAt) {
}
