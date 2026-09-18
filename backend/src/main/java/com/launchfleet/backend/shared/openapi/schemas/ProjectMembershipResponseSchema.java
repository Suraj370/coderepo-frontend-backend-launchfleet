package com.launchfleet.backend.shared.openapi.schemas;

import java.time.Instant;

import com.launchfleet.backend.users.Role;

/** OpenAPI documentation only - mirrors ProjectMembershipResource's response shape. */
public record ProjectMembershipResponseSchema(String id, String userId, String name, String email, Role role,
		Instant createdAt) {
}
