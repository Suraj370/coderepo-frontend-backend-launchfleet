package com.launchfleet.backend.featureflags.domain;

import java.time.Instant;
import java.util.List;

/**
 * A reusable, named group of users, defined by conditions - project-scoped, NOT
 * environment-scoped (a segment's membership rule is the same across every
 * environment of a project; only whether a targeting rule references it varies by
 * environment). Deliberately excludes SEGMENT_MATCH from its own conditions: a
 * segment can only be built from ATTRIBUTE/USER_KEY conditions, which prevents a
 * segment from ever referencing another segment and therefore prevents reference
 * cycles by construction (no cycle-detection algorithm needed).
 *
 * Whether a Segment referenced by an active targeting rule may be retired is NOT
 * checked here - same cross-aggregate reasoning as TargetingRule.variantId: this
 * type has no visibility into FeatureFlagConfig's targeting rules, so that check is
 * an application-layer concern (see RetireSegment).
 */
public class Segment {

	private final String id;

	private final String projectId;

	private final String key;

	private String name;

	private SegmentStatus status;

	private List<Condition> conditions;

	private final String createdBy;

	private final Instant createdAt;

	private String updatedBy;

	private Instant updatedAt;

	private Segment(String id, String projectId, String key, String name, SegmentStatus status,
			List<Condition> conditions, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
		this.id = id;
		this.projectId = projectId;
		this.key = key;
		this.name = name;
		this.status = status;
		this.conditions = List.copyOf(conditions);
		this.createdBy = createdBy;
		this.createdAt = createdAt;
		this.updatedBy = updatedBy;
		this.updatedAt = updatedAt;
	}

	public static Segment create(String projectId, String key, String name, List<Condition> conditions,
			String actingUserId) {
		validateConditions(conditions);
		Instant now = Instant.now();
		return new Segment(null, projectId, key, name, SegmentStatus.ACTIVE, conditions, actingUserId, now,
				actingUserId, now);
	}

	/** Rehydrates a segment already known to be valid, exactly as persisted - storage adapters only. */
	public static Segment reconstitute(String id, String projectId, String key, String name, SegmentStatus status,
			List<Condition> conditions, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
		return new Segment(id, projectId, key, name, status, conditions, createdBy, createdAt, updatedBy, updatedAt);
	}

	public void update(String name, List<Condition> conditions, String actingUserId) {
		validateConditions(conditions);
		this.name = name;
		this.conditions = List.copyOf(conditions);
		this.updatedBy = actingUserId;
		this.updatedAt = Instant.now();
	}

	public void retire(String actingUserId) {
		if (this.status == SegmentStatus.RETIRED) {
			throw new IllegalStateException("Segment is already retired.");
		}

		this.status = SegmentStatus.RETIRED;
		this.updatedBy = actingUserId;
		this.updatedAt = Instant.now();
	}

	private static void validateConditions(List<Condition> conditions) {
		if (conditions == null || conditions.isEmpty()) {
			throw new IllegalArgumentException("A segment must have at least one condition.");
		}

		boolean hasSegmentMatch = conditions.stream().anyMatch(condition -> condition.type() == ConditionType.SEGMENT_MATCH);
		if (hasSegmentMatch) {
			throw new IllegalArgumentException("A segment's conditions must not reference another segment.");
		}
	}

	public String getId() {
		return id;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getKey() {
		return key;
	}

	public String getName() {
		return name;
	}

	public SegmentStatus getStatus() {
		return status;
	}

	public List<Condition> getConditions() {
		return conditions;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
