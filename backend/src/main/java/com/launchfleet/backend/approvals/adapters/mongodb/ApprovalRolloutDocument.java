package com.launchfleet.backend.approvals.adapters.mongodb;

import java.util.List;

class ApprovalRolloutDocument {

	private List<ApprovalAllocationDocument> allocations;

	ApprovalRolloutDocument() {
	}

	ApprovalRolloutDocument(List<ApprovalAllocationDocument> allocations) {
		this.allocations = allocations;
	}

	List<ApprovalAllocationDocument> getAllocations() {
		return allocations;
	}

	void setAllocations(List<ApprovalAllocationDocument> allocations) {
		this.allocations = allocations;
	}
}
