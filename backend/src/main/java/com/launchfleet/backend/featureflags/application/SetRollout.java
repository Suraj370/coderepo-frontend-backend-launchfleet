package com.launchfleet.backend.featureflags.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.Rollout;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Replaces the complete rollout allocation list for a flag's config in one
 * environment - PUT semantics, not a partial update (see Rollout's Javadoc: its
 * "sums to exactly 10000" invariant can't be validated from a partial list).
 */
@Component
public class SetRollout {

	private static final int BAD_REQUEST = 400;

	private final FeatureFlagConfigStore featureFlagConfigStore;

	private final FeatureFlagLookup lookup;

	private final TargetingRuleValidator validator;

	SetRollout(FeatureFlagConfigStore featureFlagConfigStore, FeatureFlagLookup lookup,
			TargetingRuleValidator validator) {
		this.featureFlagConfigStore = featureFlagConfigStore;
		this.lookup = lookup;
		this.validator = validator;
	}

	/**
	 * requestedAllocations is deliberately raw (variantId, percentage) pairs, not
	 * already-constructed Allocation instances - Allocation's own constructor validates
	 * per-entry invariants (percentage in [0, 10000]) and throws IllegalArgumentException,
	 * which must be caught and translated to a 400 here rather than escaping from the
	 * web layer to the generic 500 handler.
	 */
	public FeatureFlagView execute(String projectKey, String flagKey, String environmentKey,
			List<AllocationInput> requestedAllocations, String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);
		FeatureFlag flag = lookup.resolveFlag(project, flagKey);
		EnvironmentRef environment = lookup.resolveEnvironment(project, environmentKey);
		FeatureFlagConfig config = lookup.resolveConfig(flag, environment);

		Rollout rollout;
		try {
			List<Allocation> allocations = requestedAllocations.stream()
					.map(input -> new Allocation(input.variantId(), input.percentage())).toList();
			rollout = new Rollout(allocations);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}

		for (Allocation allocation : rollout.allocations()) {
			validator.validateVariantBelongsToFlag(flag, allocation.variantId());
		}

		config.updateRollout(rollout, actingUserId);
		featureFlagConfigStore.save(config);

		return lookup.viewOf(flag, project);
	}

	/** A raw, unvalidated (variantId, percentage) pair from the request - see execute's Javadoc. */
	public record AllocationInput(String variantId, int percentage) {
	}
}
