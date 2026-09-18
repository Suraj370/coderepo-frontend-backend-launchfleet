package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The Mongo-mapped persistence shape of a FeatureFlag - kept separate from the
 * domain class on purpose (see FeatureFlag's Javadoc: the domain has zero Spring/
 * Mongo dependency). Timestamps are not auto-populated by Spring Data auditing here;
 * the domain stamps its own createdAt/updatedAt, and this document just stores
 * whatever value the domain already decided.
 */
@Document(collection = "feature_flags")
@CompoundIndex(name = "project_flag_key", def = "{'projectId': 1, 'key': 1}", unique = true)
class FeatureFlagDocument {

	@Id
	private String id;

	private String projectId;

	private String key;

	private String name;

	private String description;

	private String type;

	private String status;

	private List<VariantDocument> variants = new ArrayList<>();

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

	String getDescription() {
		return description;
	}

	void setDescription(String description) {
		this.description = description;
	}

	String getType() {
		return type;
	}

	void setType(String type) {
		this.type = type;
	}

	String getStatus() {
		return status;
	}

	void setStatus(String status) {
		this.status = status;
	}

	List<VariantDocument> getVariants() {
		return variants;
	}

	void setVariants(List<VariantDocument> variants) {
		this.variants = variants;
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
