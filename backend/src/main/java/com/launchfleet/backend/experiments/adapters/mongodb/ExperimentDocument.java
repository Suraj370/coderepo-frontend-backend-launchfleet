package com.launchfleet.backend.experiments.adapters.mongodb;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Mirrors FeatureFlagDocument's (projectId, key) uniqueness convention exactly. */
@Document(collection = "experiments")
@CompoundIndex(name = "project_experiment_key", def = "{'projectId': 1, 'key': 1}", unique = true)
class ExperimentDocument {

	@Id
	private String id;

	private String projectId;

	private String environmentId;

	private String featureFlagId;

	private String key;

	private String name;

	private String description;

	private List<ExperimentAllocationEntryDocument> allocation;

	private String conversionEventName;

	private String status;

	private int version;

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

	String getEnvironmentId() {
		return environmentId;
	}

	void setEnvironmentId(String environmentId) {
		this.environmentId = environmentId;
	}

	String getFeatureFlagId() {
		return featureFlagId;
	}

	void setFeatureFlagId(String featureFlagId) {
		this.featureFlagId = featureFlagId;
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

	String getDescription() {
		return description;
	}

	void setDescription(String description) {
		this.description = description;
	}

	List<ExperimentAllocationEntryDocument> getAllocation() {
		return allocation;
	}

	void setAllocation(List<ExperimentAllocationEntryDocument> allocation) {
		this.allocation = allocation;
	}

	String getConversionEventName() {
		return conversionEventName;
	}

	void setConversionEventName(String conversionEventName) {
		this.conversionEventName = conversionEventName;
	}

	String getStatus() {
		return status;
	}

	void setStatus(String status) {
		this.status = status;
	}

	int getVersion() {
		return version;
	}

	void setVersion(int version) {
		this.version = version;
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
