package com.launchfleet.backend.featureflags.adapters.mongodb;

/** The Mongo-mapped shape of a Variant, embedded within FeatureFlagDocument. */
class VariantDocument {

	private String id;

	private String key;

	private String name;

	private Object value;

	private int order;

	VariantDocument() {
	}

	VariantDocument(String id, String key, String name, Object value, int order) {
		this.id = id;
		this.key = key;
		this.name = name;
		this.value = value;
		this.order = order;
	}

	String getId() {
		return id;
	}

	void setId(String id) {
		this.id = id;
	}

	String getKey() {
		return key;
	}

	void setKey(String key) {
		this.key = key;
	}

	String getName() {
		return name;
	}

	void setName(String name) {
		this.name = name;
	}

	Object getValue() {
		return value;
	}

	void setValue(Object value) {
		this.value = value;
	}

	int getOrder() {
		return order;
	}

	void setOrder(int order) {
		this.order = order;
	}
}
