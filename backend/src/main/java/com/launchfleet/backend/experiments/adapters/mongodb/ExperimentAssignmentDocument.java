package com.launchfleet.backend.experiments.adapters.mongodb;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A separate, high-cardinality collection (locked decision 4/requirement 4) - never
 * embedded in ExperimentDocument. The unique compound index on (experimentId,
 * userKey) IS the first-write-wins mechanism: MongoExperimentAssignmentStore relies
 * on this index rejecting a second insert for the same pair, not on application-
 * level locking (locked decision 16).
 */
@Document(collection = "experiment_assignments")
@CompoundIndex(name = "experiment_user", def = "{'experimentId': 1, 'userKey': 1}", unique = true)
class ExperimentAssignmentDocument {

	@Id
	private String id;

	private String experimentId;

	private String userKey;

	private String variantId;

	private Instant assignedAt;

	String getId() {
		return id;
	}

	void setId(String id) {
		this.id = id;
	}

	String getExperimentId() {
		return experimentId;
	}

	void setExperimentId(String experimentId) {
		this.experimentId = experimentId;
	}

	String getUserKey() {
		return userKey;
	}

	void setUserKey(String userKey) {
		this.userKey = userKey;
	}

	String getVariantId() {
		return variantId;
	}

	void setVariantId(String variantId) {
		this.variantId = variantId;
	}

	Instant getAssignedAt() {
		return assignedAt;
	}

	void setAssignedAt(Instant assignedAt) {
		this.assignedAt = assignedAt;
	}
}
