package com.launchfleet.backend.projects;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.launchfleet.backend.users.Role;

/**
 * Grants one user a Role on one project. This is the sole source of truth for
 * dashboard authorization - there is no separate global role on User.
 *
 * Project itself is not a domain entity yet (it lands in a later increment),
 * so projectKey is a plain string placeholder for now, not a foreign key.
 */
@Document(collection = "project_memberships")
@CompoundIndex(name = "user_project", def = "{'userId': 1, 'projectKey': 1}", unique = true)
public class ProjectMembership {

	@Id
	private String id;

	private String userId;

	private String projectKey;

	private Role role;

	@CreatedDate
	private Instant createdAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getProjectKey() {
		return projectKey;
	}

	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
