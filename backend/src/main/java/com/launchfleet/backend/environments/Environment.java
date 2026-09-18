package com.launchfleet.backend.environments;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A project environment (e.g. development, staging, production). References Project
 * by id, not embedded - referenced independently by FeatureFlagConfig and, later, SDK
 * credentials/targeting. No CRUD API in this increment (seeded).
 */
@Document(collection = "environments")
@CompoundIndex(name = "project_env_key", def = "{'projectId': 1, 'key': 1}", unique = true)
public class Environment {

	@Id
	private String id;

	private String projectId;

	private String key;

	private String name;

	// Defaults to ACTIVE for documents persisted before Phase 6 introduced this field
	// (Spring Data leaves a missing field at its Java default on read - null for an
	// enum - so CreateEnvironment sets this explicitly rather than relying on that).
	private EnvironmentStatus status = EnvironmentStatus.ACTIVE;

	@CreatedDate
	private Instant createdAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public EnvironmentStatus getStatus() {
		return status;
	}

	public void setStatus(EnvironmentStatus status) {
		this.status = status;
	}
}
