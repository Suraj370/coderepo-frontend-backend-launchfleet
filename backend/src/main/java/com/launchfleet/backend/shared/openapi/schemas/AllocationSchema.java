package com.launchfleet.backend.shared.openapi.schemas;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI documentation only - see ConditionSchema's Javadoc. */
public record AllocationSchema(

		String variantId,

		@Schema(description = "Basis points (0-10000). All allocations in a rollout must sum to exactly 10000.") int percentage) {
}
