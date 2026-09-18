package com.launchfleet.backend.shared.openapi.schemas;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI documentation only - mirrors ExperimentResource.toMap(ExperimentVariantMetrics). */
public record ExperimentMetricsResponseSchema(

		String variantId,

		long assignedCount,

		long conversionCount,

		@Schema(description = "conversionCount / assignedCount, or 0.0 when assignedCount is 0 (never NaN/division by zero).") double conversionRate) {
}
