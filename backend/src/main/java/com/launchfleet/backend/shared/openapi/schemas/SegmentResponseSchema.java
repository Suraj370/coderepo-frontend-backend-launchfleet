package com.launchfleet.backend.shared.openapi.schemas;

import java.time.Instant;
import java.util.List;

import com.launchfleet.backend.featureflags.domain.SegmentStatus;

/** OpenAPI documentation only - mirrors SegmentResource.toMap(Segment). */
public record SegmentResponseSchema(

		String id,

		String projectId,

		String key,

		String name,

		SegmentStatus status,

		List<ConditionSchema> conditions,

		String createdBy,

		Instant createdAt,

		String updatedBy,

		Instant updatedAt) {
}
