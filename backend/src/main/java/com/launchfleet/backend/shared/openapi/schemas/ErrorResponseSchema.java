package com.launchfleet.backend.shared.openapi.schemas;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OpenAPI documentation only - mirrors GlobalExceptionHandler.envelope's exact
 * shape (the single error envelope every 4xx/5xx response in this API uses). Not
 * used as an actual controller/handler return type.
 */
@Schema(description = "The uniform error envelope returned by every failed request in this API.")
public record ErrorResponseSchema(ErrorDetailSchema error) {

	public record ErrorDetailSchema(

			@Schema(description = "A stable, machine-readable error code, e.g. VALIDATION_ERROR, FLAG_NOT_FOUND, EXPERIMENT_CONFLICT.") String code,

			@Schema(description = "A human-readable message - never includes internal/sensitive details.") String message,

			@Schema(description = "Present only for VALIDATION_ERROR: a map of field name to validation message.") Map<String, Object> details) {
	}
}
