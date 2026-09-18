package com.launchfleet.backend.approvals.adapters.mongodb;

import java.util.List;

class ApprovalTargetingRuleDocument {

	private String id;

	private int priority;

	private List<ApprovalConditionDocument> conditions;

	private String variantId;

	ApprovalTargetingRuleDocument() {
	}

	ApprovalTargetingRuleDocument(String id, int priority, List<ApprovalConditionDocument> conditions,
			String variantId) {
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

	List<ApprovalConditionDocument> getConditions() {
		return conditions;
	}

	void setConditions(List<ApprovalConditionDocument> conditions) {
		this.conditions = conditions;
	}

	String getVariantId() {
		return variantId;
	}

	void setVariantId(String variantId) {
		this.variantId = variantId;
	}
}
