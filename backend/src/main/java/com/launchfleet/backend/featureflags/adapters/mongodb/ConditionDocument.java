package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.util.List;

/** The Mongo-mapped shape of a Condition, embedded within TargetingRuleDocument/SegmentDocument. */
class ConditionDocument {

	private String type;

	private String attribute;

	private String operator;

	private List<String> values;

	ConditionDocument() {
	}

	ConditionDocument(String type, String attribute, String operator, List<String> values) {
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
