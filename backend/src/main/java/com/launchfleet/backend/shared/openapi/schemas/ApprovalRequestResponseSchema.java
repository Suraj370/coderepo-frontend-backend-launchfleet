package com.launchfleet.backend.shared.openapi.schemas;

import java.time.Instant;

import com.launchfleet.backend.approvals.domain.ApprovalStatus;
import com.launchfleet.backend.approvals.domain.CancellationReason;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI documentation only - mirrors ApprovalRequestResource.toMap(ApprovalRequest). */
public record ApprovalRequestResponseSchema(

		String id,

		String projectId,

		String featureFlagId,

		String environmentId,

		ApprovalStatus status,

		@Schema(description = "The FeatureFlagConfig version this proposal was based on - staleness is detected against this.") int baseConfigVersion,

		ProposedConfigSchema proposedConfig,

		String submittedBy,

		Instant submittedAt,

		@Schema(nullable = true) String reviewedBy,

		@Schema(nullable = true) Instant reviewedAt,

		@Schema(nullable = true) String approvalComment,

		@Schema(nullable = true) String rejectionComment,

		@Schema(nullable = true, description = "Set only once scheduled for later execution.") Instant scheduledAt,

		@Schema(nullable = true, description = "The FeatureFlagConfig version produced once APPLIED.") Integer appliedVersion,

		@Schema(nullable = true, description = "Set only when status is CANCELLED or FAILED.") CancellationReason cancellationReason,

		@Schema(nullable = true) String cancelledBy,

		@Schema(nullable = true) Instant cancelledAt) {
}
