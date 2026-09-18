package com.launchfleet.backend.approvals.adapters.mongodb;

class ApprovalAllocationDocument {

	private String variantId;

	private int percentage;

	ApprovalAllocationDocument() {
	}

	ApprovalAllocationDocument(String variantId, int percentage) {
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
