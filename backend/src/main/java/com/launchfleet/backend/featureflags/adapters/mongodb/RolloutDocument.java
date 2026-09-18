package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.util.List;

/** The Mongo-mapped shape of a Rollout, embedded within FeatureFlagConfigDocument. */
class RolloutDocument {

	private List<AllocationDocument> allocations;

	RolloutDocument() {
	}

	RolloutDocument(List<AllocationDocument> allocations) {
		this.allocations = allocations;
	}

	List<AllocationDocument> getAllocations() {
		return allocations;
	}

	void setAllocations(List<AllocationDocument> allocations) {
		this.allocations = allocations;
	}
}
