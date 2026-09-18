package com.launchfleet.backend.shared.openapi.schemas;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI documentation only - mirrors ExperimentEventResource.toMap(ExperimentEvent). */
@Schema(description = "A recorded conversion event. variantId always comes from the caller's persisted ExperimentAssignment - it is never accepted from the request body.")
public record ExperimentEventResponseSchema(

		String experimentId,

		String userKey,

		String eventName,

		String variantId,

		Instant timestamp) {
}
