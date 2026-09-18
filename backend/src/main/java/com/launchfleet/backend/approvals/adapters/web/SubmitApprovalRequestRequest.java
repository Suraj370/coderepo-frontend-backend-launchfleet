package com.launchfleet.backend.approvals.adapters.web;

import java.util.List;

import com.launchfleet.backend.featureflags.adapters.web.RolloutRequest;
import com.launchfleet.backend.featureflags.adapters.web.TargetingRuleRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Reuses the SAME request shapes the direct dashboard editing endpoints already use
 * (TargetingRuleRequest, RolloutRequest - see FeatureFlagResource) rather than a
 * second, competing set of DTOs for the same fields (locked architecture rule 29).
 * rollout is optional - a proposal need not include a rollout at all.
 */
public record SubmitApprovalRequestRequest(

		@NotNull Boolean enabled,

		String defaultVariantId,

		List<@Valid TargetingRuleRequest> targetingRules,

		@Valid RolloutRequest rollout) {
}
