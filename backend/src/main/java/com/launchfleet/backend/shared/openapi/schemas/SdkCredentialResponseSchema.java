package com.launchfleet.backend.shared.openapi.schemas;

import java.time.Instant;

import com.launchfleet.backend.sdk.SdkCredentialType;

/**
 * OpenAPI documentation only - mirrors SdkCredentialResource's response shape.
 * plaintextSecret is only ever populated in the create response, never again
 * afterward; clientSideId is only populated for CLIENT_SIDE credentials.
 */
public record SdkCredentialResponseSchema(String id, String environmentKey, SdkCredentialType type, String label,
		String clientSideId, boolean active, Instant createdAt, Instant revokedAt, Instant expiresAt,
		String plaintextSecret) {
}
