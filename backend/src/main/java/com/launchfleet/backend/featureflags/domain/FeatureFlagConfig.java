package com.launchfleet.backend.featureflags.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How one FeatureFlag behaves in one Environment - a separate aggregate from
 * FeatureFlag (see FeatureFlagService's old Javadoc, preserved in spirit: per-
 * environment version/update churn is independent and future SDK config
 * distribution needs its own query shape). Owns enable/disable state and version
 * bookkeeping as behavior, not as fields an outside caller pokes directly.
 *
 * Also owns this environment's targeting rules - embedded (no separate collection),
 * so rules and version are always persisted together in one document write. Whether
 * a rule's variantId belongs to the owning flag, and whether a SEGMENT_MATCH
 * condition's segment is valid/in-project/active, is NOT checked here - same
 * cross-aggregate reasoning as updateDefaultVariant (see TargetingRuleValidator).
 *
 * Also owns an optional Rollout - a config-level fallback used only when no
 * targeting rule matches (see the featureflags module's evaluation-order
 * documentation). Embedded, not a separate aggregate: no independent identity,
 * timestamps, or query pattern. Whether an allocation's variantId belongs to the
 * owning flag is NOT checked here - same cross-aggregate reasoning as
 * updateDefaultVariant/updateTargetingRules.
 */
public class FeatureFlagConfig {

	private final String id;

	private final String featureFlagId;

	private final String environmentId;

	private final String projectId;

	private boolean enabled;

	private String defaultVariantId;

	private List<TargetingRule> targetingRules;

	private Rollout rollout;

	private int version;

	private String updatedBy;

	private Instant updatedAt;

	private final Instant createdAt;

	private FeatureFlagConfig(String id, String featureFlagId, String environmentId, String projectId,
			boolean enabled, String defaultVariantId, List<TargetingRule> targetingRules, Rollout rollout,
			int version, String updatedBy, Instant updatedAt, Instant createdAt) {
		this.id = id;
		this.featureFlagId = featureFlagId;
		this.environmentId = environmentId;
		this.projectId = projectId;
		this.enabled = enabled;
		this.defaultVariantId = defaultVariantId;
		this.targetingRules = List.copyOf(targetingRules);
		this.rollout = rollout;
		this.version = version;
		this.updatedBy = updatedBy;
		this.updatedAt = updatedAt;
		this.createdAt = createdAt;
	}

	/** A fresh, disabled config for one flag in one environment - auto-created alongside the flag. */
	public static FeatureFlagConfig createDisabled(String featureFlagId, String environmentId, String projectId,
			String defaultVariantId, String actingUserId) {
		Instant now = Instant.now();

		return new FeatureFlagConfig(null, featureFlagId, environmentId, projectId, false, defaultVariantId,
				List.of(), null, 1, actingUserId, now, now);
	}

	/** Rehydrates a config already known to be valid, exactly as persisted - storage adapters only. */
	public static FeatureFlagConfig reconstitute(String id, String featureFlagId, String environmentId,
			String projectId, boolean enabled, String defaultVariantId, List<TargetingRule> targetingRules,
			Rollout rollout, int version, String updatedBy, Instant updatedAt, Instant createdAt) {
		return new FeatureFlagConfig(id, featureFlagId, environmentId, projectId, enabled, defaultVariantId,
				targetingRules, rollout, version, updatedBy, updatedAt, createdAt);
	}

	public void updateEnabled(boolean enabled, String actingUserId) {
		this.enabled = enabled;
		this.version = this.version + 1;
		this.updatedBy = actingUserId;
		this.updatedAt = Instant.now();
	}

	/**
	 * Whether the given id actually belongs to the owning flag is not checked here -
	 * FeatureFlagConfig doesn't hold a reference to the flag's variant list, so that
	 * cross-aggregate check is an application-layer concern (see
	 * UpdateEnvironmentConfig). This method only enforces what it can see on its own.
	 */
	public void updateDefaultVariant(String variantId, String actingUserId) {
		if (variantId == null || variantId.isBlank()) {
			throw new IllegalArgumentException("defaultVariantId is required.");
		}

		this.defaultVariantId = variantId;
		this.version = this.version + 1;
		this.updatedBy = actingUserId;
		this.updatedAt = Instant.now();
	}

	/**
	 * Replaces the complete targeting-rule list for this environment in one write,
	 * bumping version exactly once regardless of how many rules the caller is
	 * conceptually adding/updating/removing in this logical mutation. Application-level
	 * add/update/remove use cases load the current rules, compute the resulting list,
	 * and invoke this once - never several times for one request.
	 *
	 * Enforces only what this aggregate can see on its own: no duplicate rule ids, and
	 * every rule has already been validated by TargetingRule.create/withUpdatedFields
	 * (at least one condition, non-blank variantId). Ordering is stored sorted by
	 * priority (lower first, as ties broken by id for determinism) so that reads never
	 * depend on insertion order.
	 */
	public void updateTargetingRules(List<TargetingRule> rules, String actingUserId) {
		if (rules == null) {
			throw new IllegalArgumentException("rules is required.");
		}

		Set<String> seenIds = new HashSet<>();
		for (TargetingRule rule : rules) {
			if (!seenIds.add(rule.getId())) {
				throw new IllegalArgumentException("Duplicate targeting rule id: " + rule.getId());
			}
		}

		this.targetingRules = rules.stream()
				.sorted(Comparator.comparingInt(TargetingRule::getPriority).thenComparing(TargetingRule::getId))
				.toList();
		this.version = this.version + 1;
		this.updatedBy = actingUserId;
		this.updatedAt = Instant.now();
	}

	/**
	 * Replaces the rollout in one write, bumping version exactly once - a single PUT,
	 * regardless of how many allocation entries changed, is one logical mutation.
	 * Rollout validates its own invariants (non-empty, no duplicate variants, sums to
	 * exactly 10000 basis points) in its own constructor; this method only stores it.
	 */
	public void updateRollout(Rollout rollout, String actingUserId) {
		if (rollout == null) {
			throw new IllegalArgumentException("rollout is required.");
		}

		this.rollout = rollout;
		this.version = this.version + 1;
		this.updatedBy = actingUserId;
		this.updatedAt = Instant.now();
	}

	/** Clears the rollout, reverting to plain defaultVariantId fallback behavior. Bumps version exactly once. */
	public void removeRollout(String actingUserId) {
		this.rollout = null;
		this.version = this.version + 1;
		this.updatedBy = actingUserId;
		this.updatedAt = Instant.now();
	}

	/**
	 * Replaces enabled/defaultVariantId/targetingRules/rollout in one write, bumping
	 * version exactly once - used when an approved approval request's complete
	 * proposed snapshot (see the approvals module, Phase 6) is applied to the active
	 * configuration. That "V10 -> V11" invariant is exactly one step regardless of how
	 * many of these four fields the proposal actually changed, unlike the granular
	 * single-field mutators above (updateEnabled/updateDefaultVariant/
	 * updateTargetingRules/updateRollout), which each bump their own version and exist
	 * for the dashboard's field-at-a-time editing flow, not for replacing the whole
	 * configuration atomically.
	 */
	public void applyProposedConfiguration(boolean enabled, String defaultVariantId, List<TargetingRule> targetingRules,
			Rollout rollout, String actingUserId) {
		if (defaultVariantId == null || defaultVariantId.isBlank()) {
			throw new IllegalArgumentException("defaultVariantId is required.");
		}

		if (targetingRules == null) {
			throw new IllegalArgumentException("targetingRules is required.");
		}

		this.enabled = enabled;
		this.defaultVariantId = defaultVariantId;
		this.targetingRules = targetingRules.stream()
				.sorted(Comparator.comparingInt(TargetingRule::getPriority).thenComparing(TargetingRule::getId))
				.toList();
		this.rollout = rollout;
		this.version = this.version + 1;
		this.updatedBy = actingUserId;
		this.updatedAt = Instant.now();
	}

	public String getId() {
		return id;
	}

	public String getFeatureFlagId() {
		return featureFlagId;
	}

	public String getEnvironmentId() {
		return environmentId;
	}

	public String getProjectId() {
		return projectId;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public String getDefaultVariantId() {
		return defaultVariantId;
	}

	public List<TargetingRule> getTargetingRules() {
		return targetingRules;
	}

	public Rollout getRollout() {
		return rollout;
	}

	public int getVersion() {
		return version;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
