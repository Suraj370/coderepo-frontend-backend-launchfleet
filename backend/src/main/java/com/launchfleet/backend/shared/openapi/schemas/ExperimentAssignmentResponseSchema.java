package com.launchfleet.backend.shared.openapi.schemas;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI documentation only - mirrors ExperimentEventResource.toMap(ExperimentAssignment). */
@Schema(description = "A sticky, immutable record of which variant this user was assigned in this experiment. Repeated calls for the same (experiment, userKey) always return the same assignment.")
public record ExperimentAssignmentResponseSchema(

		String experimentId,

		String userKey,

		String variantId,

		Instant assignedAt) {
}
