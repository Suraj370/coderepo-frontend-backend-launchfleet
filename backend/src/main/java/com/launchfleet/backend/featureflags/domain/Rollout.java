package com.launchfleet.backend.featureflags.domain;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A percentage split across variants for one FeatureFlagConfig's fallback traffic -
 * an embedded value object, not a separate aggregate (no independent identity,
 * timestamps, or query pattern; always read/written together with its owning
 * FeatureFlagConfig). Validated in the compact constructor so an invalid Rollout
 * cannot be constructed at all: at least one allocation, no duplicate variant ids,
 * and the allocations must sum to exactly 10000 basis points (100%).
 */
public record Rollout(List<Allocation> allocations) {

	private static final int TOTAL_BASIS_POINTS = 10000;

	public Rollout {
		if (allocations == null || allocations.isEmpty()) {
			throw new IllegalArgumentException("A rollout must have at least one allocation.");
		}

		long distinctVariantIds = allocations.stream().map(Allocation::variantId).distinct().count();
		if (distinctVariantIds != allocations.size()) {
			throw new IllegalArgumentException("A rollout must not allocate the same variant more than once.");
		}

		int total = allocations.stream().mapToInt(Allocation::percentage).sum();
		if (total != TOTAL_BASIS_POINTS) {
			throw new IllegalArgumentException("Rollout allocations must sum to exactly 10000 basis points.");
		}

		allocations = List.copyOf(allocations);
	}

	/**
	 * Allocations in a stable order, independent of Mongo/list insertion order -
	 * sorted lexicographically by variantId. Used both to render a deterministic
	 * response and to build deterministic cumulative bucket ranges for assignment.
	 */
	public List<Allocation> sortedByVariantId() {
		return allocations.stream().sorted((a, b) -> a.variantId().compareTo(b.variantId()))
				.collect(Collectors.toUnmodifiableList());
	}
}
