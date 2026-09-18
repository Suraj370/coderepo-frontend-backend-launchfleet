package com.launchfleet.backend.shared.openapi.schemas;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI documentation only - see ConditionSchema's Javadoc. */
public record VariantSchema(

		String id,

		String key,

		String name,

		@Schema(description = "The variant's served value - shape depends on the flag's type.") Object value,

		int order) {
}
