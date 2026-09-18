package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The Mongo-mapped persistence shape of a Segment - a separate top-level collection
 * from feature_flag_configs, per the established persistence model (Segment is its
 * own aggregate, project-scoped and reusable across environments).
 */
@Document(collection = "segments")
@CompoundIndex(name = "project_segment_key", def = "{'projectId': 1, 'key': 1}", unique = true)
class SegmentDocument {

	@Id
	private String id;

	private String projectId;

	private String key;

	private String name;

	private String status;

	private List<ConditionDocument> conditions = new ArrayList<>();

	private String createdBy;

	private Instant createdAt;

	private String updatedBy;

	private Instant updatedAt;

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

	String getKey() {
		return key;
	}

	void setKey(String key) {
		this.key = key;
	}

	String getName() {
		return name;
	}

	void setName(String name) {
		this.name = name;
	}

	String getStatus() {
		return status;
	}

	void setStatus(String status) {
		this.status = status;
	}

	List<ConditionDocument> getConditions() {
		return conditions;
	}

	void setConditions(List<ConditionDocument> conditions) {
		this.conditions = conditions;
	}

	String getCreatedBy() {
		return createdBy;
	}

	void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	String getUpdatedBy() {
		return updatedBy;
	}

	void setUpdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}

	void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
