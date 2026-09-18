package com.launchfleet.backend.experiments.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.experiments.ports.ExperimentStore;
import com.launchfleet.backend.featureflags.application.TargetingRuleValidator;
import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.Rollout;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * DRAFT-only configuration edit (locked decision 3: "Experiment configuration must
 * be immutable once RUNNING" - enforced by Experiment.updateConfiguration itself,
 * this use case just translates the raw request shape and validates variant
 * ownership). The allocation supplied here is exactly what StartExperiment freezes
 * - reuses featureflags.domain.Rollout/Allocation directly (locked decision 6), no
 * second percentage representation.
 */
@Component
public class UpdateExperiment {

	private static final int BAD_REQUEST = 400;

	private static final int CONFLICT = 409;

	private final ExperimentLookup lookup;

	private final ExperimentStore experimentStore;

	private final TargetingRuleValidator validator;

	UpdateExperiment(ExperimentLookup lookup, ExperimentStore experimentStore, TargetingRuleValidator validator) {
		this.lookup = lookup;
		this.experimentStore = experimentStore;
		this.validator = validator;
	}

	public Experiment execute(String projectKey, String experimentKey, String name, String description,
			List<AllocationInput> allocationInputs, String conversionEventName, String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);
		Experiment experiment = lookup.resolveExperiment(project, experimentKey);
		FeatureFlag flag = lookup.resolveFlagById(experiment.getFeatureFlagId());

		Rollout allocation = buildAllocation(flag, allocationInputs);

		try {
			experiment.updateConfiguration(name, description, allocation, conversionEventName, actingUserId);
		} catch (IllegalStateException exception) {
			throw new ApiException(CONFLICT, "INVALID_TRANSITION", exception.getMessage());
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}

		return experimentStore.save(experiment);
	}

	private Rollout buildAllocation(FeatureFlag flag, List<AllocationInput> allocationInputs) {
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

	/** A raw, unvalidated (variantId, percentage) pair - see SetRollout.AllocationInput for the same shape used elsewhere. */
	public record AllocationInput(String variantId, int percentage) {
	}
}
