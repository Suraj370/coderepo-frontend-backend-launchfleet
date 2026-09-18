package com.launchfleet.backend.shared.openapi.schemas;

import java.time.Instant;

import com.launchfleet.backend.users.Role;

/** OpenAPI documentation only - mirrors ProjectResource's response shape. */
public record ProjectResponseSchema(String id, String key, String name, Role role, Instant createdAt) {
}
