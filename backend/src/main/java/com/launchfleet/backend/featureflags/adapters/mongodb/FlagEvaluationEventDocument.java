package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Raw evaluation-count events only, mirroring ExperimentEventDocument. Indexed on
 * (projectId, timestamp) to support the dashboard summary's day-bucketed count
 * queries.
 */
@Document(collection = "flag_evaluation_events")
@CompoundIndex(name = "project_timestamp", def = "{'projectId': 1, 'timestamp': 1}")
class FlagEvaluationEventDocument {

	@Id
	private String id;

	private String projectId;

	private String environmentId;

	private String flagId;

	private String userKey;

	private String variantId;

	private Instant timestamp;

	String getId() {
		return id;
	}

	void setId(String id) {
		this.id = id;
	}

	String getProjectId() {
		return projectId;
	}

	void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	String getEnvironmentId() {
		return environmentId;
	}

	void setEnvironmentId(String environmentId) {
		this.environmentId = environmentId;
	}

	String getFlagId() {
		return flagId;
	}

	void setFlagId(String flagId) {
		this.flagId = flagId;
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

	Instant getTimestamp() {
		return timestamp;
	}

	void setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
	}
}
