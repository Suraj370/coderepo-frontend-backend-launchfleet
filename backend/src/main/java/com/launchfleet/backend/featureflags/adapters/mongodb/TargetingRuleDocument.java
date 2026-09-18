package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.util.List;

/** The Mongo-mapped shape of a TargetingRule, embedded within FeatureFlagConfigDocument. */
class TargetingRuleDocument {

	private String id;

	private int priority;

	private List<ConditionDocument> conditions;

	private String variantId;

	TargetingRuleDocument() {
	}

	TargetingRuleDocument(String id, int priority, List<ConditionDocument> conditions, String variantId) {
		this.id = id;
		this.priority = priority;
		this.conditions = conditions;
		this.variantId = variantId;
	}

	String getId() {
		return id;
	}

	void setId(String id) {
		this.id = id;
	}

	int getPriority() {
		return priority;
	}

	void setPriority(int priority) {
		this.priority = priority;
	}

	List<ConditionDocument> getConditions() {
		return conditions;
	}

	void setConditions(List<ConditionDocument> conditions) {
		this.conditions = conditions;
	}

	String getVariantId() {
		return variantId;
	}

	void setVariantId(String variantId) {
		this.variantId = variantId;
	}
}
