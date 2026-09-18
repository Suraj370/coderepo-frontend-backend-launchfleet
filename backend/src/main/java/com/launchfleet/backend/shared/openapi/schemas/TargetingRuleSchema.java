package com.launchfleet.backend.shared.openapi.schemas;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI documentation only - see ConditionSchema's Javadoc. */
@Schema(description = "IF all conditions match THEN serve variantId. Evaluated in ascending priority order.")
public record TargetingRuleSchema(

		String id,

		@Schema(description = "Lower priority is evaluated first.") int priority,

		@Schema(description = "AND-combined; a rule must have at least one condition.") List<ConditionSchema> conditions,

		String variantId) {
}
