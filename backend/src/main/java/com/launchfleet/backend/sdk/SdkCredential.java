package com.launchfleet.backend.sdk;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Represents the project/environment the credential belongs to, not a human
 * user. SERVER credentials store only a SHA-256 hash of the secret (never the
 * plaintext, which is shown once at creation); CLIENT_SIDE ids are not secret
 * so they're stored and looked up in plaintext.
 */
@Document(collection = "sdk_credentials")
public class SdkCredential {

	@Id
	private String id;

	private String projectKey;

	private String environmentKey;

	private SdkCredentialType type;

	private String label;

	@Indexed(unique = true, sparse = true)
	private String secretHash;

	@Indexed(unique = true, sparse = true)
	private String clientSideId;

	private boolean active = true;

	@CreatedDate
	private Instant createdAt;

	private Instant revokedAt;

	/** Null means "does not expire" - most SDK credentials today. */
	private Instant expiresAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getProjectKey() {
		return projectKey;
	}

	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}

	public String getEnvironmentKey() {
		return environmentKey;
	}

	public void setEnvironmentKey(String environmentKey) {
		this.environmentKey = environmentKey;
	}

	public SdkCredentialType getType() {
		return type;
	}

	public void setType(SdkCredentialType type) {
		this.type = type;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getSecretHash() {
		return secretHash;
	}

	public void setSecretHash(String secretHash) {
		this.secretHash = secretHash;
	}

	public String getClientSideId() {
		return clientSideId;
	}

	public void setClientSideId(String clientSideId) {
		this.clientSideId = clientSideId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public void setRevokedAt(Instant revokedAt) {
		this.revokedAt = revokedAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}
}
