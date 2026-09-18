package com.launchfleet.backend.shared.openapi.schemas;

/** OpenAPI documentation only - mirrors AuthService.session(Authentication)'s Map shape. */
public record SessionResponseSchema(UserResponseSchema user) {
}
