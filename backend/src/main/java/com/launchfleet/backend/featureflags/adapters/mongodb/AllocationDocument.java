package com.launchfleet.backend.featureflags.adapters.mongodb;

/** The Mongo-mapped shape of an Allocation, embedded within RolloutDocument. */
class AllocationDocument {

	private String variantId;

	private int percentage;

	AllocationDocument() {
	}

	AllocationDocument(String variantId, int percentage) {
		this.variantId = variantId;
		this.percentage = percentage;
	}

	String getVariantId() {
		return variantId;
	}

	void setVariantId(String variantId) {
		this.variantId = variantId;
	}

	int getPercentage() {
		return percentage;
	}

	void setPercentage(int percentage) {
		this.percentage = percentage;
	}
}
