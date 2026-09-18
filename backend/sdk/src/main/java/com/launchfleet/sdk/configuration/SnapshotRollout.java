package com.launchfleet.sdk.configuration;

import java.util.Comparator;
import java.util.List;

/**
 * allocations is always stored pre-sorted by variantId (see #of), matching the
 * backend's Rollout.sortedByVariantId() ordering exactly - the rollout hashing
 * algorithm's cumulative-range walk depends on this order being identical to the
 * server's, not on Mongo/list insertion order (see RolloutHasher).
 */
public record SnapshotRollout(List<SnapshotAllocation> allocations) {

	public static SnapshotRollout of(List<SnapshotAllocation> allocations) {
		List<SnapshotAllocation> sorted = allocations.stream()
				.sorted(Comparator.comparing(SnapshotAllocation::variantId)).toList();

		return new SnapshotRollout(sorted);
	}
}
