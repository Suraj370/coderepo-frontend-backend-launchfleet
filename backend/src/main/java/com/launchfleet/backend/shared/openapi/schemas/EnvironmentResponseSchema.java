package com.launchfleet.backend.shared.openapi.schemas;

import java.time.Instant;

import com.launchfleet.backend.environments.EnvironmentStatus;

/** OpenAPI documentation only - mirrors EnvironmentResource.toMap(Environment). */
public record EnvironmentResponseSchema(

		String id,

		String projectId,

		String key,

		String name,

		EnvironmentStatus status,

		Instant createdAt) {
}
