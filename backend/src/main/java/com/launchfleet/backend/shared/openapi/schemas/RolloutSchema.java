package com.launchfleet.backend.shared.openapi.schemas;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI documentation only - see ConditionSchema's Javadoc. */
@Schema(description = "A percentage-based traffic split across variants, used as a fallback when no targeting rule matches.")
public record RolloutSchema(List<AllocationSchema> allocations) {
}
