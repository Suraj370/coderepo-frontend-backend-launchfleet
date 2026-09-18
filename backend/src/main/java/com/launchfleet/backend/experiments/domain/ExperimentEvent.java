package com.launchfleet.backend.experiments.domain;

import java.time.Instant;

/**
 * A raw conversion event (locked decision 7 - raw events, no rollup collection).
 * variantId is always the PERSISTED ASSIGNMENT's variant (locked decision 9), never
 * a client-supplied value - by the time this object is constructed (see
 * RecordExperimentEvent), the variant has already been resolved from
 * ExperimentAssignment, not from the request. Immutable once created, like
 * ExperimentAssignment - a conversion event is a fact about something that already
 * happened, never edited afterward.
 */
public final class ExperimentEvent {

	private final String id;

	private final String projectId;

	private final String environmentId;

	private final String experimentId;

	private final String userKey;

	private final String eventName;

	private final String variantId;

	private final Instant timestamp;

	private ExperimentEvent(String id, String projectId, String environmentId, String experimentId, String userKey,
			String eventName, String variantId, Instant timestamp) {
		this.id = id;
		this.projectId = projectId;
		this.environmentId = environmentId;
		this.experimentId = experimentId;
		this.userKey = userKey;
		this.eventName = eventName;
		this.variantId = variantId;
		this.timestamp = timestamp;
	}

	/** A brand-new event, not yet persisted. variantId must already be resolved from a persisted assignment - see this class's Javadoc. */
	public static ExperimentEvent create(String projectId, String environmentId, String experimentId, String userKey,
			String eventName, String variantId) {
		if (projectId == null || projectId.isBlank()) {
			throw new IllegalArgumentException("projectId is required.");
		}
		if (environmentId == null || environmentId.isBlank()) {
			throw new IllegalArgumentException("environmentId is required.");
		}
		if (experimentId == null || experimentId.isBlank()) {
			throw new IllegalArgumentException("experimentId is required.");
		}
		if (userKey == null || userKey.isBlank()) {
			throw new IllegalArgumentException("userKey is required.");
		}
		if (eventName == null || eventName.isBlank()) {
			throw new IllegalArgumentException("eventName is required.");
		}
		if (variantId == null || variantId.isBlank()) {
			throw new IllegalArgumentException("variantId is required.");
		}

		return new ExperimentEvent(null, projectId, environmentId, experimentId, userKey, eventName, variantId,
				Instant.now());
	}

	/** Rehydrates an event already known to be valid, exactly as persisted - storage adapters only. */
	public static ExperimentEvent reconstitute(String id, String projectId, String environmentId,
			String experimentId, String userKey, String eventName, String variantId, Instant timestamp) {
		return new ExperimentEvent(id, projectId, environmentId, experimentId, userKey, eventName, variantId,
				timestamp);
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

	public String getExperimentId() {
		return experimentId;
	}

	public String getUserKey() {
		return userKey;
	}

	public String getEventName() {
		return eventName;
	}

	public String getVariantId() {
		return variantId;
	}

	public Instant getTimestamp() {
		return timestamp;
	}
}
