package com.launchfleet.backend.featureflags.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FlagEvaluationEvent;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FlagEvaluationEventStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Records that a flag was evaluated for one end-user - purely a counter for the
 * dashboard's evaluation-volume chart, not itself part of flag evaluation (which
 * happens entirely client-side from the SDK's cached config; see
 * SdkConfigurationResource). Project/environment scope comes only from the
 * authenticated SdkCredential, mirroring RecordExperimentEvent.
 */
@Component
public class RecordFlagEvaluation {

	private static final int BAD_REQUEST = 400;

	private final FeatureFlagLookup lookup;

	private final FlagEvaluationEventStore flagEvaluationEventStore;

	RecordFlagEvaluation(FeatureFlagLookup lookup, FlagEvaluationEventStore flagEvaluationEventStore) {
		this.lookup = lookup;
		this.flagEvaluationEventStore = flagEvaluationEventStore;
	}

	public FlagEvaluationEvent execute(String projectKey, String environmentKey, String flagKey, String userKey,
			String variantId) {
		if (userKey == null || userKey.isBlank()) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", "userKey is required.");
		}
		if (variantId == null || variantId.isBlank()) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", "variantId is required.");
		}

		ProjectRef project = lookup.resolveProject(projectKey);
		FeatureFlag flag = lookup.resolveFlag(project, flagKey);
		EnvironmentRef environment = lookup.resolveEnvironment(project, environmentKey);

		FlagEvaluationEvent event = FlagEvaluationEvent.create(project.id(), environment.id(), flag.getId(), userKey,
				variantId);

		return flagEvaluationEventStore.save(event);
	}
}
