package com.launchfleet.backend.experiments.adapters.mongodb;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Raw conversion events only (locked decision 7/10) - no rollup collection, no TTL
 * (locked decision 17). Two indexes: one matching the metric query's actual access
 * pattern (experimentId + variantId + eventName, to support the distinct-user-count
 * query MongoExperimentEventStore runs), and one matching the explicitly requested
 * "experimentId + timestamp" shape for any time-ordered access.
 */
@Document(collection = "experiment_events")
@CompoundIndexes({
		@CompoundIndex(name = "experiment_variant_event", def = "{'experimentId': 1, 'variantId': 1, 'eventName': 1}"),
		@CompoundIndex(name = "experiment_timestamp", def = "{'experimentId': 1, 'timestamp': 1}") })
class ExperimentEventDocument {

	@Id
	private String id;

	private String projectId;

	private String environmentId;

	private String experimentId;

	private String userKey;

	private String eventName;

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

	String getEventName() {
		return eventName;
	}

	void setEventName(String eventName) {
		this.eventName = eventName;
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
