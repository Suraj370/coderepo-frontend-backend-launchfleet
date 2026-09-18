package com.launchfleet.sdk.api;

import com.launchfleet.sdk.configuration.SnapshotVariant;

/**
 * The full, structured evaluation outcome. Public convenience methods
 * (LaunchFleetClient.getBoolean/getVariant) are built on top of this and return plain
 * values with a caller-supplied fallback; ordinary SDK usage never needs to construct
 * or inspect this type directly, but it's here for callers that want the reason/version.
 *
 * hasVariant() is false only for FLAG_NOT_FOUND/NO_CONFIGURATION - every other reason
 * (including DISABLED/NO_USER_KEY/DEFAULT) always carries a real variant (typically the
 * flag's defaultVariant), matching the server's own "no applicable rule/rollout ->
 * defaultVariant" semantics.
 */
public record EvaluationDetail(String variantKey, Object value, EvaluationReason reason, String configurationVersion) {

	public boolean hasVariant() {
		return variantKey != null;
	}

	public static EvaluationDetail of(SnapshotVariant variant, EvaluationReason reason, String configurationVersion) {
		return new EvaluationDetail(variant.key(), variant.value(), reason, configurationVersion);
	}

	public static EvaluationDetail flagNotFound(String configurationVersion) {
		return new EvaluationDetail(null, null, EvaluationReason.FLAG_NOT_FOUND, configurationVersion);
	}

	public static EvaluationDetail noConfiguration() {
		return new EvaluationDetail(null, null, EvaluationReason.NO_CONFIGURATION, null);
	}
}
