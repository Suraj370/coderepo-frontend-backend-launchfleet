package com.launchfleet.backend.approvals.domain;

import java.util.List;

import com.launchfleet.backend.featureflags.domain.Rollout;
import com.launchfleet.backend.featureflags.domain.TargetingRule;

/**
 * The complete proposed environment configuration an ApprovalRequest carries - reuses
 * the SAME domain types FeatureFlagConfig itself is built from (TargetingRule,
 * Rollout), rather than a second, competing representation (see the locked
 * architecture's rule 29: "Do not create a second competing representation of flag
 * configuration"). Their own constructors already enforce every structural invariant
 * (condition shape, rollout summing to 10000 basis points, etc.) - this record adds no
 * validation of its own beyond what those types already guarantee.
 *
 * Immutable: once an ApprovalRequest is constructed with a ProposedConfig, nothing
 * about it can change. A different proposal is a new ApprovalRequest, never an edit to
 * this one (see ApprovalRequest's Javadoc).
 */
public record ProposedConfig(boolean enabled, String defaultVariantId, List<TargetingRule> targetingRules,
		Rollout rollout) {

	public ProposedConfig {
		if (defaultVariantId == null || defaultVariantId.isBlank()) {
			throw new IllegalArgumentException("defaultVariantId is required.");
		}

		if (targetingRules == null) {
			throw new IllegalArgumentException("targetingRules is required.");
		}

		targetingRules = List.copyOf(targetingRules);
	}
}
