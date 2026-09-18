package com.launchfleet.backend.experiments.application;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.experiments.domain.ExperimentAssignment;
import com.launchfleet.backend.experiments.domain.ExperimentStatus;
import com.launchfleet.backend.experiments.domain.ExperimentVariantAssigner;
import com.launchfleet.backend.experiments.ports.ExperimentAssignmentStore;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.FlagStatus;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Establishes (or returns the existing, sticky) assignment for a user in a
 * RUNNING experiment - the SDK-facing entry point that Decision 9's "events
 * reference a persisted assignment" model requires something to exist for (locked
 * decisions 3/4/9). Project/environment scope comes entirely from the caller-
 * resolved ProjectRef/EnvironmentRef (in the web layer, derived from the
 * authenticated SdkCredential - see ExperimentEventResource), never from a
 * request body field.
 *
 * Ordering is deliberate and load-bearing: the existing-assignment lookup
 * (experimentId, userKey) happens FIRST, before any FeatureFlag/environment/
 * FeatureFlagConfig state is even loaded. A persisted assignment is authoritative
 * and sticky (locked decision 4) - it is returned as-is and none of the flag's
 * current targeting/enabled/retirement state is re-consulted for it. Only when no
 * assignment exists yet does this method load the flag/environment/config and run
 * FlagTargetingEligibility against them; an ineligible user gets no assignment at
 * all (USER_NOT_ELIGIBLE), and an eligible one gets the experiment's own frozen
 * allocation (locked decision 6) - never the flag's live rollout - via
 * ExperimentVariantAssigner (a deterministic, experiment-scoped primitive kept
 * separate from featureflags.domain.RolloutAssigner; see its Javadoc for why).
 * Concurrent first-assignment races are resolved by ExperimentAssignmentStore's
 * unique-index first-write-wins semantics (locked decision 16): the losing caller
 * here still returns whatever assignmentStore.getOrCreate hands back, which is the
 * document that actually won, not this thread's own locally computed variant.
 */
@Component
public class CreateExperimentAssignment {

	private static final int BAD_REQUEST = 400;

	private static final int CONFLICT = 409;

	private final ExperimentLookup lookup;

	private final FeatureFlagConfigStore featureFlagConfigStore;

	private final ExperimentAssignmentStore assignmentStore;

	private final FlagTargetingEligibility eligibility;

	CreateExperimentAssignment(ExperimentLookup lookup, FeatureFlagConfigStore featureFlagConfigStore,
			ExperimentAssignmentStore assignmentStore, FlagTargetingEligibility eligibility) {
		this.lookup = lookup;
		this.featureFlagConfigStore = featureFlagConfigStore;
		this.assignmentStore = assignmentStore;
		this.eligibility = eligibility;
	}

	public ExperimentAssignment execute(String projectKey, String environmentKey, String experimentKey,
			String userKey) {
		if (userKey == null || userKey.isBlank()) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", "userKey is required.");
		}

		ProjectRef project = lookup.resolveProject(projectKey);
		EnvironmentRef environment = lookup.resolveEnvironment(project, environmentKey);
		Experiment experiment = resolveExperimentInEnvironment(project, environment, experimentKey);

		if (experiment.getStatus() != ExperimentStatus.RUNNING) {
			throw new ApiException(CONFLICT, "EXPERIMENT_NOT_RUNNING",
					"This experiment is not currently running; no new assignments can be created.");
		}

		Optional<ExperimentAssignment> existing = assignmentStore.findByExperimentIdAndUserKey(experiment.getId(),
				userKey);
		if (existing.isPresent()) {
			return existing.get();
		}

		return createNewAssignment(environment, experiment, userKey);
	}

	private ExperimentAssignment createNewAssignment(EnvironmentRef environment, Experiment experiment,
			String userKey) {
		FeatureFlag flag = lookup.resolveFlagById(experiment.getFeatureFlagId());
		if (flag.getStatus() == FlagStatus.RETIRED) {
			throw new ApiException(CONFLICT, "FLAG_RETIRED", "The referenced flag has been retired.");
		}
		if (!environment.active()) {
			throw new ApiException(CONFLICT, "ENVIRONMENT_RETIRED", "The referenced environment has been retired.");
		}

		FeatureFlagConfig config = featureFlagConfigStore
				.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id())
				.orElseThrow(() -> new ApiException(CONFLICT, "CONFIG_NOT_FOUND",
						"No configuration for this flag in this environment."));
		if (!config.isEnabled()) {
			throw new ApiException(CONFLICT, "FLAG_DISABLED",
					"The referenced flag is currently disabled in this environment; no new assignments can be created.");
		}

		if (!eligibility.isEligible(config, userKey)) {
			throw new ApiException(CONFLICT, "USER_NOT_ELIGIBLE",
					"This user does not match the flag's current targeting and is not eligible for this experiment.");
		}

		String variantId = ExperimentVariantAssigner.assign(environment.id(), experiment.getId(), userKey,
				experiment.getAllocation()).orElseThrow(
						() -> new IllegalStateException("A non-blank userKey must always produce an assignment."));

		return assignmentStore.getOrCreate(ExperimentAssignment.create(experiment.getId(), userKey, variantId));
	}

	private Experiment resolveExperimentInEnvironment(ProjectRef project, EnvironmentRef environment,
			String experimentKey) {
		Experiment experiment = lookup.resolveExperiment(project, experimentKey);
		if (!experiment.getEnvironmentId().equals(environment.id())) {
			throw new ApiException(404, "EXPERIMENT_NOT_FOUND", "No experiment with that key in this environment.");
		}

		return experiment;
	}
}
