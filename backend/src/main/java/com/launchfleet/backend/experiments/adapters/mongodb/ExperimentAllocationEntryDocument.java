package com.launchfleet.backend.experiments.adapters.mongodb;

/** Mirrors featureflags.adapters.mongodb.AllocationDocument's shape - this module keeps its own Mongo mapping, not a shared one (same reasoning as the approvals module's embedded documents). */
class ExperimentAllocationEntryDocument {

	private String variantId;

	private int percentage;

	ExperimentAllocationEntryDocument() {
	}

	ExperimentAllocationEntryDocument(String variantId, int percentage) {
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
