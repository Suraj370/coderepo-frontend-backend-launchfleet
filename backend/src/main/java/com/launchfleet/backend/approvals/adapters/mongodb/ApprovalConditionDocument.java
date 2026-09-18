package com.launchfleet.backend.approvals.adapters.mongodb;

import java.util.List;

/** Mirrors featureflags.adapters.mongodb.ConditionDocument's shape - this module keeps its own Mongo mapping, not a shared one (see MongoApprovalRequestStore's Javadoc). */
class ApprovalConditionDocument {

	private String type;

	private String attribute;

	private String operator;

	private List<String> values;

	ApprovalConditionDocument() {
	}

	ApprovalConditionDocument(String type, String attribute, String operator, List<String> values) {
		this.type = type;
		this.attribute = attribute;
		this.operator = operator;
		this.values = values;
	}

	String getType() {
		return type;
	}

	void setType(String type) {
		this.type = type;
	}

	String getAttribute() {
		return attribute;
	}

	void setAttribute(String attribute) {
		this.attribute = attribute;
	}

	String getOperator() {
		return operator;
	}

	void setOperator(String operator) {
		this.operator = operator;
	}

	List<String> getValues() {
		return values;
	}

	void setValues(List<String> values) {
		this.values = values;
	}
}
