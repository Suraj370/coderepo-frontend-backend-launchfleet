package com.launchfleet.backend.shared.openapi.schemas;

import java.time.Instant;

import com.launchfleet.backend.activity.ActivityAction;

/** OpenAPI documentation only - mirrors ActivityResource's response shape. */
public record ActivityLogResponseSchema(String id, String actorUserId, String actorName, ActivityAction action,
		String subjectType, String subjectKey, String environmentId, Instant occurredAt) {
}
