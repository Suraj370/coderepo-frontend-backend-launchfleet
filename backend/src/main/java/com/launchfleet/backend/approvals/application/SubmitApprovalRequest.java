package com.launchfleet.backend.approvals.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.activity.ActivityAction;
import com.launchfleet.backend.activity.ActivityRecorder;
import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.domain.ProposedConfig;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.launchfleet.backend.featureflags.application.TargetingRuleValidator;
import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.FlagStatus;
import com.launchfleet.backend.featureflags.domain.Rollout;
import com.launchfleet.backend.featureflags.domain.TargetingRule;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Implements the locked proposal-creation flow (architecture rule 7): load the
 * current active config, capture its version as baseConfigVersion, build and
 * validate the proposed snapshot using the SAME validation the dashboard's direct
 * targeting/rollout endpoints use (TargetingRuleValidator, TargetingRule/Rollout's
 * own constructors), verify flag/environment are active, verify no PENDING request
 * already exists for this scope, then store the immutable proposal.
 *
 * Project/environment/flag access is scoped through ApprovalRequestLookup exactly
 * like every other featureflags use case - a caller can never propose against a
 * project/environment/flag combination their session doesn't already have EDITOR
 * access to (enforced by @PreAuthorize on the controller, see
 * ApprovalRequestResource), and never against one that doesn't exist (404s here).
 */
@Component
public class SubmitApprovalRequest {

	private static final int BAD_REQUEST = 400;

	private static final int CONFLICT = 409;

	private static final int NOT_FOUND = 404;

	private final ApprovalRequestLookup lookup;

	private final ApprovalRequestStore approvalRequestStore;

	private final FeatureFlagConfigStore featureFlagConfigStore;

	private final TargetingRuleValidator validator;

	private final ActivityRecorder activityLogService;

	SubmitApprovalRequest(ApprovalRequestLookup lookup, ApprovalRequestStore approvalRequestStore,
			FeatureFlagConfigStore featureFlagConfigStore, TargetingRuleValidator validator,
			ActivityRecorder activityLogService) {
		this.lookup = lookup;
		this.approvalRequestStore = approvalRequestStore;
		this.featureFlagConfigStore = featureFlagConfigStore;
		this.validator = validator;
		this.activityLogService = activityLogService;
	}

	public ApprovalRequest execute(String projectKey, String flagKey, String environmentKey, boolean enabled,
			String defaultVariantId, List<TargetingRuleInput> targetingRuleInputs,
			List<AllocationInput> rolloutAllocations, String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);
		FeatureFlag flag = lookup.resolveFlag(project, flagKey);
		EnvironmentRef environment = lookup.resolveEnvironment(project, environmentKey);

		if (flag.getStatus() == FlagStatus.RETIRED) {
			throw new ApiException(CONFLICT, "FLAG_RETIRED", "A retired flag cannot receive a new proposal.");
		}
		if (!environment.active()) {
			throw new ApiException(CONFLICT, "ENVIRONMENT_RETIRED",
					"A retired environment cannot receive a new proposal.");
		}

		FeatureFlagConfig currentConfig = featureFlagConfigStore
				.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id())
				.orElseThrow(() -> new ApiException(NOT_FOUND, "CONFIG_NOT_FOUND",
						"No configuration for this flag in this environment."));

		validator.validateVariantBelongsToFlag(flag, defaultVariantId);

		List<TargetingRule> targetingRules = buildTargetingRules(project, flag, targetingRuleInputs);
		Rollout rollout = buildRollout(flag, rolloutAllocations);

		if (approvalRequestStore.findPendingByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id())
				.isPresent()) {
			throw new ApiException(CONFLICT, "PENDING_REQUEST_EXISTS",
					"A pending approval request already exists for this flag and environment.");
		}

		ProposedConfig proposedConfig;
		ApprovalRequest request;
		try {
			proposedConfig = new ProposedConfig(enabled, defaultVariantId, targetingRules, rollout);
			request = ApprovalRequest.propose(project.id(), flag.getId(), environment.id(), currentConfig.getVersion(),
					proposedConfig, actingUserId);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}

		ApprovalRequest saved = approvalRequestStore.save(request);

		activityLogService.record(project.id(), actingUserId, ActivityAction.APPROVAL_SUBMITTED, "approval",
				flag.getKey(), environment.id());

		return saved;
	}

	private List<TargetingRule> buildTargetingRules(ProjectRef project, FeatureFlag flag,
			List<TargetingRuleInput> inputs) {
		if (inputs == null) {
			return List.of();
		}

		try {
			return inputs.stream().map(input -> {
				validator.validateVariantBelongsToFlag(flag, input.variantId());
				validator.validateSegmentReferences(project, input.conditions());

				return TargetingRule.create(input.priority(), input.conditions(), input.variantId());
			}).toList();
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}
	}

	private Rollout buildRollout(FeatureFlag flag, List<AllocationInput> allocationInputs) {
		if (allocationInputs == null || allocationInputs.isEmpty()) {
			return null;
		}

		try {
			List<Allocation> allocations = allocationInputs.stream()
					.map(input -> new Allocation(input.variantId(), input.percentage())).toList();
			for (Allocation allocation : allocations) {
				validator.validateVariantBelongsToFlag(flag, allocation.variantId());
			}

			return new Rollout(allocations);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}
	}

	/** A raw, unvalidated targeting rule from the request - see AddTargetingRule for the same shape used elsewhere. */
	public record TargetingRuleInput(int priority, List<Condition> conditions, String variantId) {
	}

	/** A raw, unvalidated (variantId, percentage) pair - see SetRollout.AllocationInput for the same shape used elsewhere. */
	public record AllocationInput(String variantId, int percentage) {
	}
}
