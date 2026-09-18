package com.launchfleet.backend.experiments.domain;

import java.time.Instant;

import com.launchfleet.backend.featureflags.domain.Rollout;

/**
 * The Phase 7 aggregate (locked decision 1: a separate aggregate referencing
 * FeatureFlag, not metadata attached to it, and not an independent flag/evaluation
 * system). References projectId/environmentId/featureFlagId by id only - exactly
 * the same shape ApprovalRequest already uses to reference a flag/environment
 * without owning them.
 *
 * Does NOT duplicate FeatureFlag's variant definitions (locked decision 5): the
 * frozen `allocation` field IS the experiment's variant reference list - each
 * Allocation.variantId() must belong to the referenced FeatureFlag (validated in
 * the application layer, by TargetingRuleValidator.validateVariantBelongsToFlag,
 * exactly like the dashboard's own rollout-editing use case already does; see
 * CreateExperiment/UpdateExperiment). Reuses featureflags.domain.Rollout/Allocation
 * directly rather than a second percentage representation (locked decision 6) -
 * Rollout's own constructor already enforces non-empty, unique variant ids, and
 * summing to exactly 10000 basis points.
 *
 * Configuration (name, description, allocation, conversionEventName) is mutable
 * only in DRAFT - locked decision 3's "Experiment configuration must be immutable
 * once RUNNING" is enforced here, not left to the application layer to remember.
 * The allocation set here is what start() freezes; there is no further mechanism
 * anywhere in this class (or the application layer) that ever re-reads the
 * referenced FeatureFlag's live rollout after that point.
 */
public final class Experiment {

	private final String id;

	private final String projectId;

	private final String environmentId;

	private final String featureFlagId;

	private final String key;

	private String name;

	private String description;

	private Rollout allocation;

	private String conversionEventName;

	private ExperimentStatus status;

	private int version;

	private final String createdBy;

	private final Instant createdAt;

	private String updatedBy;

	private Instant updatedAt;

	private Experiment(String id, String projectId, String environmentId, String featureFlagId, String key,
			String name, String description, Rollout allocation, String conversionEventName,
			ExperimentStatus status, int version, String createdBy, Instant createdAt, String updatedBy,
			Instant updatedAt) {
		this.id = id;
		this.projectId = projectId;
		this.environmentId = environmentId;
		this.featureFlagId = featureFlagId;
		this.key = key;
		this.name = name;
		this.description = description;
		this.allocation = allocation;
		this.conversionEventName = conversionEventName;
		this.status = status;
		this.version = version;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
		this.updatedBy = updatedBy;
		this.updatedAt = updatedAt;
	}

	/** A fresh experiment, always DRAFT - allocation/conversionEventName are configured afterward via updateConfiguration, before start(). */
	public static Experiment create(String projectId, String environmentId, String featureFlagId, String key,
			String name, String description, String actingUserId) {
		if (projectId == null || projectId.isBlank()) {
			throw new IllegalArgumentException("projectId is required.");
		}
		if (environmentId == null || environmentId.isBlank()) {
			throw new IllegalArgumentException("environmentId is required.");
		}
		if (featureFlagId == null || featureFlagId.isBlank()) {
			throw new IllegalArgumentException("featureFlagId is required.");
		}
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("key is required.");
		}
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name is required.");
		}

		Instant now = Instant.now();
		return new Experiment(null, projectId, environmentId, featureFlagId, key, name, description, null, null,
				ExperimentStatus.DRAFT, 1, actingUserId, now, actingUserId, now);
	}

	/** Rehydrates an experiment already known to be valid, exactly as persisted - storage adapters only. */
	public static Experiment reconstitute(String id, String projectId, String environmentId, String featureFlagId,
			String key, String name, String description, Rollout allocation, String conversionEventName,
			ExperimentStatus status, int version, String createdBy, Instant createdAt, String updatedBy,
			Instant updatedAt) {
		return new Experiment(id, projectId, environmentId, featureFlagId, key, name, description, allocation,
				conversionEventName, status, version, createdBy, createdAt, updatedBy, updatedAt);
	}

	/**
	 * DRAFT-only configuration mutation - name/description/allocation/
	 * conversionEventName all change together in one call, one version bump,
	 * mirroring FeatureFlagConfig's single-field-mutators-bump-once convention scaled
	 * to "the whole draft configuration is one logical edit." Rejects outright once
	 * the experiment has left DRAFT (locked decision 3).
	 */
	public void updateConfiguration(String name, String description, Rollout allocation, String conversionEventName,
			String actingUserId) {
		requireStatus(ExperimentStatus.DRAFT, "reconfigure");
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name is required.");
		}

		this.name = name;
		this.description = description;
		this.allocation = allocation;
		this.conversionEventName = conversionEventName;
		this.version = this.version + 1;
		this.updatedBy = actingUserId;
		this.updatedAt = Instant.now();
	}

	/**
	 * DRAFT -> RUNNING. Requires a complete, valid configuration (locked decision 12:
	 * "An experiment must have the required valid configuration before it can
	 * transition from DRAFT to RUNNING") - a frozen allocation and a conversion event
	 * name. From this point on, allocation is fixed for the life of the experiment;
	 * no code path anywhere re-reads the flag's live rollout to refresh it.
	 */
	public void start(String actingUserId) {
		requireStatus(ExperimentStatus.DRAFT, "start");
		if (allocation == null) {
			throw new IllegalStateException("An experiment needs a traffic allocation before it can start.");
		}
		if (conversionEventName == null || conversionEventName.isBlank()) {
			throw new IllegalStateException("An experiment needs a conversion event name before it can start.");
		}

		this.status = ExperimentStatus.RUNNING;
		this.version = this.version + 1;
		this.updatedBy = actingUserId;
		this.updatedAt = Instant.now();
	}

	/** RUNNING -> COMPLETED. A deliberate stop, distinct from CANCELLED (which means the experiment became invalid, not that it concluded). */
	public void complete(String actingUserId) {
		requireStatus(ExperimentStatus.RUNNING, "complete");

		this.status = ExperimentStatus.COMPLETED;
		this.version = this.version + 1;
		this.updatedBy = actingUserId;
		this.updatedAt = Instant.now();
	}

	/**
	 * DRAFT or RUNNING -> CANCELLED. Used when the referenced flag/environment is
	 * retired out from under the experiment (locked decision 12) - a DRAFT experiment
	 * can never validly start against a retired flag either, so both starting
	 * statuses are eligible. Terminal, like COMPLETED - neither can transition
	 * further.
	 */
	public void cancel(String actingUserId) {
		if (status != ExperimentStatus.DRAFT && status != ExperimentStatus.RUNNING) {
			throw new IllegalStateException("Only a DRAFT or RUNNING experiment can be cancelled (was " + status + ").");
		}

		this.status = ExperimentStatus.CANCELLED;
		this.version = this.version + 1;
		this.updatedBy = actingUserId;
		this.updatedAt = Instant.now();
	}

	private void requireStatus(ExperimentStatus expected, String action) {
		if (status != expected) {
			throw new IllegalStateException(
					"Cannot " + action + " an experiment that is " + status + " (expected " + expected + ").");
		}
	}

	public String getId() {
		return id;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getEnvironmentId() {
		return environmentId;
	}

	public String getFeatureFlagId() {
		return featureFlagId;
	}

	public String getKey() {
		return key;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Rollout getAllocation() {
		return allocation;
	}

	public String getConversionEventName() {
		return conversionEventName;
	}

	public ExperimentStatus getStatus() {
		return status;
	}

	public int getVersion() {
		return version;
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
