package com.launchfleet.backend.experiments.domain;

import java.time.Instant;

/**
 * A sticky, immutable record of "this user was assigned this variant in this
 * experiment" (locked decision 4). Persisted specifically for experimentation -
 * this does NOT change Phase 4's rule that ordinary progressive-rollout assignments
 * are never persisted; that rule is untouched and this is a deliberately separate,
 * narrowly-scoped exception to it.
 *
 * Immutable by design: once created, nothing about an assignment ever changes
 * (locked decision 4 - "do not overwrite an existing assignment simply because the
 * experiment configuration later changes"). There is no update method here at all,
 * on purpose - "first write wins" is enforced by a MongoDB unique index at the
 * storage layer (see ExperimentAssignmentStore/MongoExperimentAssignmentStore), not
 * by anything in this class.
 */
public final class ExperimentAssignment {

	private final String id;

	private final String experimentId;

	private final String userKey;

	private final String variantId;

	private final Instant assignedAt;

	private ExperimentAssignment(String id, String experimentId, String userKey, String variantId,
			Instant assignedAt) {
		this.id = id;
		this.experimentId = experimentId;
		this.userKey = userKey;
		this.variantId = variantId;
		this.assignedAt = assignedAt;
	}

	/** A brand-new assignment, not yet persisted (id is null until the store assigns one). */
	public static ExperimentAssignment create(String experimentId, String userKey, String variantId) {
		if (experimentId == null || experimentId.isBlank()) {
			throw new IllegalArgumentException("experimentId is required.");
		}
		if (userKey == null || userKey.isBlank()) {
			throw new IllegalArgumentException("userKey is required.");
		}
		if (variantId == null || variantId.isBlank()) {
			throw new IllegalArgumentException("variantId is required.");
		}

		return new ExperimentAssignment(null, experimentId, userKey, variantId, Instant.now());
	}

	/** Rehydrates an assignment already known to be valid, exactly as persisted - storage adapters only. */
	public static ExperimentAssignment reconstitute(String id, String experimentId, String userKey, String variantId,
			Instant assignedAt) {
		return new ExperimentAssignment(id, experimentId, userKey, variantId, assignedAt);
	}

	public String getId() {
		return id;
	}

	public String getExperimentId() {
		return experimentId;
	}

	public String getUserKey() {
		return userKey;
	}

	public String getVariantId() {
		return variantId;
	}

	public Instant getAssignedAt() {
		return assignedAt;
	}
}
