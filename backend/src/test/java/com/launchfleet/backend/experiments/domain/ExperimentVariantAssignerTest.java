package com.launchfleet.backend.experiments.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.domain.Rollout;

/**
 * Pure unit tests for the experiment-scoped bucket-assignment primitive - mirrors
 * featureflags.domain.RolloutAssignerTest's shape, since the underlying algorithm
 * is deliberately identical (see ExperimentVariantAssigner's Javadoc for why this
 * is a separate primitive rather than a reuse of RolloutAssigner itself).
 */
class ExperimentVariantAssignerTest {

	private static final Rollout TWO_WAY_SPLIT = new Rollout(
			List.of(new Allocation("variant-a", 4000), new Allocation("variant-b", 6000)));

	@Test
	void sameInputsAlwaysProduceTheSameBucket() {
		int first = ExperimentVariantAssigner.bucketFor("env-1", "exp-1", "user-1");
		int second = ExperimentVariantAssigner.bucketFor("env-1", "exp-1", "user-1");

		assertThat(first).isEqualTo(second);
	}

	@Test
	void bucketsAreAlwaysWithinRange() {
		for (int i = 0; i < 200; i++) {
			int bucket = ExperimentVariantAssigner.bucketFor("env-1", "exp-1", "user-" + i);
			assertThat(bucket).isBetween(0, 9999);
		}
	}

	@Test
	void differentExperimentsOnTheSameFlagProduceIndependentBuckets() {
		// Not asserting they always differ (a collision is possible for any one user),
		// but across many users two different experimentIds must not always agree - if
		// they did, experimentId would not actually be part of the hash input.
		int agreements = 0;
		for (int i = 0; i < 200; i++) {
			String userKey = "user-" + i;
			int bucketExpA = ExperimentVariantAssigner.bucketFor("env-1", "exp-a", userKey);
			int bucketExpB = ExperimentVariantAssigner.bucketFor("env-1", "exp-b", userKey);
			if (bucketExpA == bucketExpB) {
				agreements++;
			}
		}

		assertThat(agreements).isLessThan(200);
	}

	@Test
	void sameUserAndExperimentAcrossRepeatedCallsAlwaysProducesTheSameVariant() {
		Optional<String> first = ExperimentVariantAssigner.assign("env-1", "exp-1", "user-1", TWO_WAY_SPLIT);
		Optional<String> second = ExperimentVariantAssigner.assign("env-1", "exp-1", "user-1", TWO_WAY_SPLIT);
		Optional<String> third = ExperimentVariantAssigner.assign("env-1", "exp-1", "user-1", TWO_WAY_SPLIT);

		assertThat(first).isEqualTo(second).isEqualTo(third);
	}

	@Test
	void missingUserKeyReturnsNoAssignment() {
		assertThat(ExperimentVariantAssigner.assign("env-1", "exp-1", null, TWO_WAY_SPLIT)).isEmpty();
	}

	@Test
	void blankUserKeyReturnsNoAssignment() {
		assertThat(ExperimentVariantAssigner.assign("env-1", "exp-1", "   ", TWO_WAY_SPLIT)).isEmpty();
	}

	@Test
	void aHundredPercentAllocationAlwaysSelectsThatVariant() {
		Rollout allIn = new Rollout(List.of(new Allocation("variant-only", 10000)));

		for (int i = 0; i < 50; i++) {
			assertThat(ExperimentVariantAssigner.assign("env-1", "exp-1", "user-" + i, allIn))
					.contains("variant-only");
		}
	}

	@Test
	void exactBucketBoundariesAreAssignedToTheCorrectVariant() {
		Rollout rollout = new Rollout(List.of(new Allocation("a", 4000), new Allocation("b", 6000)));

		assertThat(ExperimentVariantAssigner.variantForBucket(0, rollout)).contains("a");
		assertThat(ExperimentVariantAssigner.variantForBucket(3999, rollout)).contains("a");
		assertThat(ExperimentVariantAssigner.variantForBucket(4000, rollout)).contains("b");
		assertThat(ExperimentVariantAssigner.variantForBucket(9999, rollout)).contains("b");
	}
}
