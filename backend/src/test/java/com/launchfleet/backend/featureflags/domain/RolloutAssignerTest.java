package com.launchfleet.backend.featureflags.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests of the rollout-assignment primitive - no Spring, no MongoDB, no
 * I/O. Boundary tests use variantForBucket with synthetic integer buckets rather
 * than relying on real SHA-256 output happening to land on a boundary by chance;
 * hashing itself (bucketFor) is tested separately for determinism/independence.
 */
class RolloutAssignerTest {

	private static final Rollout TWO_WAY_SPLIT = new Rollout(
			List.of(new Allocation("variant-a", 4000), new Allocation("variant-b", 6000)));

	/**
	 * Buckets computed independently of this implementation (openssl/sha256sum for the
	 * digest bytes, then the same bit-assembly + Math.floorMod semantics this class
	 * uses), not just derived from this class's own output - the same golden values are
	 * asserted against the SDK's RolloutHasher (see RolloutHasherCompatibilityTest),
	 * which is what actually proves the two independent implementations agree
	 * byte-for-byte rather than merely agreeing with each other by a shared mistake.
	 */
	@Test
	void bucketsMatchAnIndependentlyComputedGoldenValue() {
		assertThat(RolloutAssigner.bucketFor("env-1", "checkout", "user-1")).isEqualTo(9586);
		assertThat(RolloutAssigner.bucketFor("env-dev", "checkout", "user-1")).isEqualTo(9390);
		assertThat(RolloutAssigner.bucketFor("env-1", "checkout", "alice")).isEqualTo(7552);
	}

	@Test
	void sameInputsAlwaysProduceTheSameBucket() {
		int first = RolloutAssigner.bucketFor("env-1", "checkout", "user-1");
		int second = RolloutAssigner.bucketFor("env-1", "checkout", "user-1");

		assertThat(first).isEqualTo(second);
	}

	@Test
	void bucketsAreAlwaysWithinRange() {
		for (int i = 0; i < 200; i++) {
			int bucket = RolloutAssigner.bucketFor("env-1", "checkout", "user-" + i);
			assertThat(bucket).isBetween(0, 9999);
		}
	}

	@Test
	void differentEnvironmentsProduceIndependentBuckets() {
		// Not asserting they always differ (a collision is possible for any one user),
		// but across many users the two environments must not always agree - if they
		// did, environmentId would not actually be part of the hash input.
		int agreements = 0;
		for (int i = 0; i < 200; i++) {
			String userKey = "user-" + i;
			int devBucket = RolloutAssigner.bucketFor("env-dev", "checkout", userKey);
			int prodBucket = RolloutAssigner.bucketFor("env-prod", "checkout", userKey);
			if (devBucket == prodBucket) {
				agreements++;
			}
		}

		assertThat(agreements).isLessThan(200);
	}

	@Test
	void sameUserAndFlagAcrossRepeatedEvaluatorCallsAlwaysProducesTheSameVariant() {
		Optional<String> first = RolloutAssigner.assign("env-1", "checkout", "user-1", TWO_WAY_SPLIT);
		Optional<String> second = RolloutAssigner.assign("env-1", "checkout", "user-1", TWO_WAY_SPLIT);
		Optional<String> third = RolloutAssigner.assign("env-1", "checkout", "user-1", TWO_WAY_SPLIT);

		assertThat(first).isEqualTo(second).isEqualTo(third);
	}

	@Test
	void missingUserKeyReturnsNoAssignment() {
		assertThat(RolloutAssigner.assign("env-1", "checkout", null, TWO_WAY_SPLIT)).isEmpty();
	}

	@Test
	void blankUserKeyReturnsNoAssignment() {
		assertThat(RolloutAssigner.assign("env-1", "checkout", "   ", TWO_WAY_SPLIT)).isEmpty();
	}

	@Test
	void aHundredPercentAllocationAlwaysSelectsThatVariant() {
		Rollout allIn = new Rollout(List.of(new Allocation("variant-only", 10000)));

		for (int i = 0; i < 50; i++) {
			assertThat(RolloutAssigner.assign("env-1", "checkout", "user-" + i, allIn))
					.contains("variant-only");
		}
	}

	@Test
	void aZeroPercentAllocationIsNeverSelected() {
		Rollout rollout = new Rollout(List.of(new Allocation("never", 0), new Allocation("always", 10000)));

		for (int i = 0; i < 200; i++) {
			assertThat(RolloutAssigner.assign("env-1", "checkout", "user-" + i, rollout)).contains("always");
		}
	}

	@Test
	void exactBucketBoundariesAreAssignedToTheCorrectVariant() {
		Rollout rollout = new Rollout(List.of(new Allocation("a", 4000), new Allocation("b", 6000)));

		assertThat(RolloutAssigner.variantForBucket(0, rollout)).contains("a");
		assertThat(RolloutAssigner.variantForBucket(3999, rollout)).contains("a");
		assertThat(RolloutAssigner.variantForBucket(4000, rollout)).contains("b");
		assertThat(RolloutAssigner.variantForBucket(9999, rollout)).contains("b");
	}

	@Test
	void cumulativeBoundariesAcrossThreeAllocationsAreRespected() {
		Rollout rollout = new Rollout(
				List.of(new Allocation("a", 1000), new Allocation("b", 2000), new Allocation("c", 7000)));

		assertThat(RolloutAssigner.variantForBucket(999, rollout)).contains("a");
		assertThat(RolloutAssigner.variantForBucket(1000, rollout)).contains("b");
		assertThat(RolloutAssigner.variantForBucket(2999, rollout)).contains("b");
		assertThat(RolloutAssigner.variantForBucket(3000, rollout)).contains("c");
		assertThat(RolloutAssigner.variantForBucket(9999, rollout)).contains("c");
	}

	@Test
	void allocationOrderingIsDeterministicRegardlessOfConstructionOrder() {
		Rollout constructedZetaFirst = new Rollout(List.of(new Allocation("zeta", 4000), new Allocation("alpha", 6000)));
		Rollout constructedAlphaFirst = new Rollout(List.of(new Allocation("alpha", 6000), new Allocation("zeta", 4000)));

		for (int bucket : new int[] { 0, 3999, 5999, 6000, 9999 }) {
			assertThat(RolloutAssigner.variantForBucket(bucket, constructedZetaFirst))
					.isEqualTo(RolloutAssigner.variantForBucket(bucket, constructedAlphaFirst));
		}
	}

	@Test
	void differentUsersDistributeAcrossBucketsWithinAGenerousTolerance() {
		// Fixed, deterministic set of user keys - not randomly generated per run - to
		// avoid flaky assertions. Tolerance is intentionally generous (target 40%/60%,
		// accept anywhere in [25%, 55%] for the smaller allocation) since this asserts
		// rough distribution, not exact counts.
		Map<String, Integer> counts = new HashMap<>();
		int sampleSize = 5000;

		for (int i = 0; i < sampleSize; i++) {
			String variant = RolloutAssigner.assign("env-1", "checkout", "user-" + i, TWO_WAY_SPLIT).orElseThrow();
			counts.merge(variant, 1, Integer::sum);
		}

		double variantAShare = counts.getOrDefault("variant-a", 0) / (double) sampleSize;
		assertThat(variantAShare).isBetween(0.25, 0.55);
		assertThat(counts.get("variant-a") + counts.get("variant-b")).isEqualTo(sampleSize);
	}

	@Test
	void increasingAnAllocationPreservesUsersAlreadyInsideItsOriginalRange() {
		Rollout before = new Rollout(List.of(new Allocation("a", 1000), new Allocation("b", 9000)));
		Rollout after = new Rollout(List.of(new Allocation("a", 2500), new Allocation("b", 7500)));

		// Every user assigned to "a" under the smaller allocation must still be
		// assigned to "a" after growing its share - this is the "stable under
		// monotonic growth" property the locked architecture relies on instead of
		// persisted sticky assignments.
		for (int i = 0; i < 500; i++) {
			String userKey = "user-" + i;
			Optional<String> beforeAssignment = RolloutAssigner.assign("env-1", "checkout", userKey, before);
			if (beforeAssignment.isPresent() && beforeAssignment.get().equals("a")) {
				Optional<String> afterAssignment = RolloutAssigner.assign("env-1", "checkout", userKey, after);
				assertThat(afterAssignment).contains("a");
			}
		}
	}
}
