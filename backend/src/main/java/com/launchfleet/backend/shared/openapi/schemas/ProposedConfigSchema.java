package com.launchfleet.backend.shared.openapi.schemas;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI documentation only - mirrors ApprovalRequestResource.toMap(ProposedConfig). */
@Schema(description = "The immutable, complete configuration snapshot this approval request proposes.")
public record ProposedConfigSchema(

		boolean enabled,

		String defaultVariantId,

		List<TargetingRuleSchema> targetingRules,

		RolloutSchema rollout) {
}
