package com.launchfleet.backend.activity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A denormalized, append-only record of one notable dashboard action.
 * actorName is captured at the time of the action (resolved once, in
 * ActivityLogService), not looked up live from User - an entry still reads
 * sensibly after the actor's name changes or their account is deactivated.
 */
@Document(collection = "activity_log")
public class ActivityLog {

	@Id
	private String id;

	@Indexed
	private String projectId;

	private String actorUserId;

	private String actorName;

	private ActivityAction action;

	private String subjectType;

	private String subjectKey;

	private String environmentId;

	private Instant occurredAt;

	public static ActivityLog record(String projectId, String actorUserId, String actorName, ActivityAction action,
			String subjectType, String subjectKey, String environmentId) {
		ActivityLog entry = new ActivityLog();
		entry.projectId = projectId;
		entry.actorUserId = actorUserId;
		entry.actorName = actorName;
		entry.action = action;
		entry.subjectType = subjectType;
		entry.subjectKey = subjectKey;
		entry.environmentId = environmentId;
		entry.occurredAt = Instant.now();

		return entry;
	}

	public String getId() {
		return id;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getActorUserId() {
		return actorUserId;
	}

	public String getActorName() {
		return actorName;
	}

	public ActivityAction getAction() {
		return action;
	}

	public String getSubjectType() {
		return subjectType;
	}

	public String getSubjectKey() {
		return subjectKey;
	}

	public String getEnvironmentId() {
		return environmentId;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}
}
