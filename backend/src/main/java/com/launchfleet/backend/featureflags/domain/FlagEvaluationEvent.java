package com.launchfleet.backend.featureflags.domain;

import java.time.Instant;

/**
 * A raw evaluation-count event - purely a counter feeding the dashboard's
 * evaluation-volume chart, not itself part of flag evaluation (which happens
 * entirely client-side from the SDK's cached config; see SdkConfigurationResource).
 * Immutable once created, mirroring ExperimentEvent's own shape and rationale.
 */
public final class FlagEvaluationEvent {

	private final String id;

	private final String projectId;

	private final String environmentId;

	private final String flagId;

	private final String userKey;

	private final String variantId;

	private final Instant timestamp;

	private FlagEvaluationEvent(String id, String projectId, String environmentId, String flagId, String userKey,
			String variantId, Instant timestamp) {
		this.id = id;
		this.projectId = projectId;
		this.environmentId = environmentId;
		this.flagId = flagId;
		this.userKey = userKey;
		this.variantId = variantId;
		this.timestamp = timestamp;
	}

	/** A brand-new event, not yet persisted. */
	public static FlagEvaluationEvent create(String projectId, String environmentId, String flagId, String userKey,
			String variantId) {
		if (projectId == null || projectId.isBlank()) {
			throw new IllegalArgumentException("projectId is required.");
		}
		if (environmentId == null || environmentId.isBlank()) {
			throw new IllegalArgumentException("environmentId is required.");
		}
		if (flagId == null || flagId.isBlank()) {
			throw new IllegalArgumentException("flagId is required.");
		}
		if (userKey == null || userKey.isBlank()) {
			throw new IllegalArgumentException("userKey is required.");
		}
		if (variantId == null || variantId.isBlank()) {
			throw new IllegalArgumentException("variantId is required.");
		}

		return new FlagEvaluationEvent(null, projectId, environmentId, flagId, userKey, variantId, Instant.now());
	}

	/** Rehydrates an event already known to be valid, exactly as persisted - storage adapters only. */
	public static FlagEvaluationEvent reconstitute(String id, String projectId, String environmentId, String flagId,
			String userKey, String variantId, Instant timestamp) {
		return new FlagEvaluationEvent(id, projectId, environmentId, flagId, userKey, variantId, timestamp);
	}

	public String getId() {
		return id;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getEnvironmentId() {
		return environmentId;
	}

	public String getFlagId() {
		return flagId;
	}

	public String getUserKey() {
		return userKey;
	}

	public String getVariantId() {
		return variantId;
	}

	public Instant getTimestamp() {
		return timestamp;
	}
}
