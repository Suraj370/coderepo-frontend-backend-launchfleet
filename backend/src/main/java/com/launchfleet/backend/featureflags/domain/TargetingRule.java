package com.launchfleet.backend.featureflags.domain;

import java.util.List;
import java.util.UUID;

/**
 * IF conditions match THEN serve variantId. Embedded within FeatureFlagConfig (see
 * that class's Javadoc): a rule has no identity or query pattern outside its owning
 * config. Conditions within one rule are AND-combined; a rule must have at least one
 * condition (an empty-condition rule would be indistinguishable from just changing
 * the default variant).
 *
 * Whether variantId actually belongs to the owning FeatureFlag is NOT checked here -
 * same reasoning as FeatureFlagConfig.updateDefaultVariant: this type has no
 * reference to FeatureFlag's variant list, so that cross-aggregate check is an
 * application-layer concern (see TargetingRuleValidator).
 */
public class TargetingRule {

	private final String id;

	private final int priority;

	private final List<Condition> conditions;

	private final String variantId;

	private TargetingRule(String id, int priority, List<Condition> conditions, String variantId) {
		this.id = id;
		this.priority = priority;
		this.conditions = List.copyOf(conditions);
		this.variantId = variantId;
	}

	/**
	 * A new rule. Unlike FeatureFlag/FeatureFlagConfig (whose ids are assigned by the
	 * storage adapter on first save), a rule's id is generated immediately - it's
	 * addressed by the caller (for update/remove) before the owning FeatureFlagConfig
	 * is ever persisted with this rule in it.
	 */
	public static TargetingRule create(int priority, List<Condition> conditions, String variantId) {
		return validated(new TargetingRule(UUID.randomUUID().toString(), priority, conditions, variantId));
	}

	/** Rehydrates a rule already known to be valid, exactly as persisted - storage adapters only. */
	public static TargetingRule reconstitute(String id, int priority, List<Condition> conditions, String variantId) {
		return new TargetingRule(id, priority, conditions, variantId);
	}

	/** Same rule, same id, replaced priority/conditions/variant - used when updating one rule within a config's list. */
	public TargetingRule withUpdatedFields(int priority, List<Condition> conditions, String variantId) {
		return validated(new TargetingRule(this.id, priority, conditions, variantId));
	}

	private static TargetingRule validated(TargetingRule rule) {
		if (rule.conditions.isEmpty()) {
			throw new IllegalArgumentException("A targeting rule must have at least one condition.");
		}

		if (rule.variantId == null || rule.variantId.isBlank()) {
			throw new IllegalArgumentException("variantId is required.");
		}

		return rule;
	}

	public String getId() {
		return id;
	}

	public int getPriority() {
		return priority;
	}

	public List<Condition> getConditions() {
		return conditions;
	}

	public String getVariantId() {
		return variantId;
	}
}
