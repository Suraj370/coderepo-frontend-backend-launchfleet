package com.launchfleet.sdk.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.launchfleet.sdk.configuration.SnapshotAllocation;
import com.launchfleet.sdk.configuration.SnapshotRollout;

/**
 * The exact fixtures from the backend's RolloutAssignerTest, run against RolloutHasher
 * instead - this is what proves "the SDK uses exactly the same algorithm and byte
 * ordering as the existing RolloutAssigner" rather than merely asserting internal
 * self-consistency. Any accidental divergence (byte order, mod base, sort order) in
 * either implementation would break one of these mirrored assertions.
 */
class RolloutHasherCompatibilityTest {

	private static final SnapshotRollout TWO_WAY_SPLIT = SnapshotRollout
			.of(List.of(new SnapshotAllocation("variant-a", 4000), new SnapshotAllocation("variant-b", 6000)));

	/**
	 * Buckets computed independently of BOTH Java implementations: openssl/sha256sum
	 * for the digest bytes, then the exact same bit-assembly Math.floorMod semantics
	 * RolloutAssigner/RolloutHasher use (a Java int built from 4 unsigned bytes is
	 * signed, so Math.floorMod on it is NOT the same as a true unsigned mod - this is a
	 * property of the established algorithm itself, reproduced deliberately, not a bug
	 * introduced here). This is what actually proves byte-ordering/algorithm agreement,
	 * rather than two Java implementations merely agreeing with each other by a shared
	 * mistake - see the identical assertions in the backend's RolloutAssignerTest.
	 */
	@Test
	void bucketsMatchAnIndependentlyComputedGoldenValue() {
		assertThat(RolloutHasher.bucketFor("env-1", "checkout", "user-1")).isEqualTo(9586);
		assertThat(RolloutHasher.bucketFor("env-dev", "checkout", "user-1")).isEqualTo(9390);
		assertThat(RolloutHasher.bucketFor("env-1", "checkout", "alice")).isEqualTo(7552);
	}

	@Test
	void sameInputsAlwaysProduceTheSameBucket() {
		int first = RolloutHasher.bucketFor("env-1", "checkout", "user-1");
		int second = RolloutHasher.bucketFor("env-1", "checkout", "user-1");

		assertThat(first).isEqualTo(second);
	}

	@Test
	void bucketsAreAlwaysWithinRange() {
		for (int i = 0; i < 200; i++) {
			int bucket = RolloutHasher.bucketFor("env-1", "checkout", "user-" + i);
			assertThat(bucket).isBetween(0, 9999);
		}
	}

	@Test
	void differentEnvironmentsProduceIndependentBuckets() {
		int agreements = 0;
		for (int i = 0; i < 200; i++) {
			String userKey = "user-" + i;
			int devBucket = RolloutHasher.bucketFor("env-dev", "checkout", userKey);
			int prodBucket = RolloutHasher.bucketFor("env-prod", "checkout", userKey);
			if (devBucket == prodBucket) {
				agreements++;
			}
		}

		assertThat(agreements).isLessThan(200);
	}

	@Test
	void missingUserKeyReturnsNoAssignment() {
		assertThat(RolloutHasher.assign("env-1", "checkout", null, TWO_WAY_SPLIT)).isEmpty();
	}

	@Test
	void blankUserKeyReturnsNoAssignment() {
		assertThat(RolloutHasher.assign("env-1", "checkout", "   ", TWO_WAY_SPLIT)).isEmpty();
	}

	@Test
	void aHundredPercentAllocationAlwaysSelectsThatVariant() {
		SnapshotRollout allIn = SnapshotRollout.of(List.of(new SnapshotAllocation("variant-only", 10000)));

		for (int i = 0; i < 50; i++) {
			assertThat(RolloutHasher.assign("env-1", "checkout", "user-" + i, allIn)).contains("variant-only");
		}
	}

	@Test
	void aZeroPercentAllocationIsNeverSelected() {
		SnapshotRollout rollout = SnapshotRollout
				.of(List.of(new SnapshotAllocation("never", 0), new SnapshotAllocation("always", 10000)));

		for (int i = 0; i < 200; i++) {
			assertThat(RolloutHasher.assign("env-1", "checkout", "user-" + i, rollout)).contains("always");
		}
	}

	@Test
	void exactBucketBoundariesAreAssignedToTheCorrectVariant() {
		SnapshotRollout rollout = SnapshotRollout
				.of(List.of(new SnapshotAllocation("a", 4000), new SnapshotAllocation("b", 6000)));

		assertThat(RolloutHasher.variantForBucket(0, rollout)).contains("a");
		assertThat(RolloutHasher.variantForBucket(3999, rollout)).contains("a");
		assertThat(RolloutHasher.variantForBucket(4000, rollout)).contains("b");
		assertThat(RolloutHasher.variantForBucket(9999, rollout)).contains("b");
	}

	@Test
	void cumulativeBoundariesAcrossThreeAllocationsAreRespected() {
		SnapshotRollout rollout = SnapshotRollout.of(List.of(new SnapshotAllocation("a", 1000),
				new SnapshotAllocation("b", 2000), new SnapshotAllocation("c", 7000)));

		assertThat(RolloutHasher.variantForBucket(999, rollout)).contains("a");
		assertThat(RolloutHasher.variantForBucket(1000, rollout)).contains("b");
		assertThat(RolloutHasher.variantForBucket(2999, rollout)).contains("b");
		assertThat(RolloutHasher.variantForBucket(3000, rollout)).contains("c");
		assertThat(RolloutHasher.variantForBucket(9999, rollout)).contains("c");
	}

	@Test
	void allocationOrderingIsDeterministicRegardlessOfConstructionOrder() {
		SnapshotRollout constructedZetaFirst = SnapshotRollout
				.of(List.of(new SnapshotAllocation("zeta", 4000), new SnapshotAllocation("alpha", 6000)));
		SnapshotRollout constructedAlphaFirst = SnapshotRollout
				.of(List.of(new SnapshotAllocation("alpha", 6000), new SnapshotAllocation("zeta", 4000)));

		for (int bucket : new int[] { 0, 3999, 5999, 6000, 9999 }) {
			assertThat(RolloutHasher.variantForBucket(bucket, constructedZetaFirst))
					.isEqualTo(RolloutHasher.variantForBucket(bucket, constructedAlphaFirst));
		}
	}

	@Test
	void differentUsersDistributeAcrossBucketsWithinAGenerousTolerance() {
		java.util.Map<String, Integer> counts = new java.util.HashMap<>();
		int sampleSize = 5000;

		for (int i = 0; i < sampleSize; i++) {
			String variant = RolloutHasher.assign("env-1", "checkout", "user-" + i, TWO_WAY_SPLIT).orElseThrow();
			counts.merge(variant, 1, Integer::sum);
		}

		double variantAShare = counts.getOrDefault("variant-a", 0) / (double) sampleSize;
		assertThat(variantAShare).isBetween(0.25, 0.55);
		assertThat(counts.get("variant-a") + counts.get("variant-b")).isEqualTo(sampleSize);
	}

	@Test
	void sameUserAndFlagAcrossRepeatedCallsAlwaysProducesTheSameVariant() {
		Optional<String> first = RolloutHasher.assign("env-1", "checkout", "user-1", TWO_WAY_SPLIT);
		Optional<String> second = RolloutHasher.assign("env-1", "checkout", "user-1", TWO_WAY_SPLIT);
		Optional<String> third = RolloutHasher.assign("env-1", "checkout", "user-1", TWO_WAY_SPLIT);

		assertThat(first).isEqualTo(second).isEqualTo(third);
	}
}
