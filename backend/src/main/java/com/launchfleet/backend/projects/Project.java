package com.launchfleet.backend.projects;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A minimal identity anchor - just enough for FeatureFlag/Environment to hold a real,
 * stable projectId instead of a bare string. No CRUD API in this increment (seeded);
 * ProjectMembership/SdkCredential keep using their existing projectKey string
 * convention unchanged - this does not replace or migrate that.
 */
@Document(collection = "projects")
public class Project {

	@Id
	private String id;

	@Indexed(unique = true)
	private String key;

	private String name;

	@CreatedDate
	private Instant createdAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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
}
