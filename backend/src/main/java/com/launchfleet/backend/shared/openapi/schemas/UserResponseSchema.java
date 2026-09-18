package com.launchfleet.backend.shared.openapi.schemas;

/** OpenAPI documentation only - mirrors User.toPublicMap(). Deliberately excludes passwordHash and any other internal field. */
public record UserResponseSchema(String id, String name, String email) {
}
