package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "feature_flag_configs")
@CompoundIndexes({
		@CompoundIndex(name = "flag_environment", def = "{'featureFlagId': 1, 'environmentId': 1}", unique = true),
		@CompoundIndex(name = "project_environment", def = "{'projectId': 1, 'environmentId': 1}") })
class FeatureFlagConfigDocument {

	@Id
	private String id;

	private String featureFlagId;

	private String environmentId;

	private String projectId;

	private boolean enabled;

	private String defaultVariantId;

	private List<TargetingRuleDocument> targetingRules;

	private RolloutDocument rollout;

	private int version;

	private String updatedBy;

	private Instant updatedAt;

	private Instant createdAt;

	String getId() {
		return id;
	}

	void setId(String id) {
		this.id = id;
	}

	String getFeatureFlagId() {
		return featureFlagId;
	}

	void setFeatureFlagId(String featureFlagId) {
		this.featureFlagId = featureFlagId;
	}

	String getEnvironmentId() {
		return environmentId;
	}

	void setEnvironmentId(String environmentId) {
		this.environmentId = environmentId;
	}

	String getProjectId() {
		return projectId;
	}

	void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	boolean isEnabled() {
		return enabled;
	}

	void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	String getDefaultVariantId() {
		return defaultVariantId;
	}

	void setDefaultVariantId(String defaultVariantId) {
		this.defaultVariantId = defaultVariantId;
	}

	List<TargetingRuleDocument> getTargetingRules() {
		return targetingRules;
	}

	void setTargetingRules(List<TargetingRuleDocument> targetingRules) {
		this.targetingRules = targetingRules;
	}

	RolloutDocument getRollout() {
		return rollout;
	}

	void setRollout(RolloutDocument rollout) {
		this.rollout = rollout;
	}

	int getVersion() {
		return version;
	}

	void setVersion(int version) {
		this.version = version;
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

	Instant getCreatedAt() {
		return createdAt;
	}

	void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
