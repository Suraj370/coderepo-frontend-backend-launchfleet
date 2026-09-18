package com.launchfleet.sdk.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests of the backoff calculation - no I/O, no scheduler, no real delays
 * waited out. BackoffPolicy.nextDelay is a stateless function of consecutiveFailures;
 * the failure counter's own lifecycle (increments on failure, resets on success) is a
 * ConfigurationCache concern, tested in ConfigurationCacheTest instead.
 */
class BackoffPolicyTest {

	private static final long BASE_MILLIS = Duration.ofSeconds(1).toMillis();

	private static final long MAX_MILLIS = Duration.ofMinutes(5).toMillis();

	@Test
	void firstFailureUsesTheInitialBackoffRange() {
		// consecutiveFailures=1 -> shift 0 -> exponential == capped == BASE_MILLIS, and
		// nextDelay's own floor (Math.max(jittered, baseMillis)) clamps every possible
		// jittered value in [capped/2, capped] up to exactly baseMillis - so the very
		// first failure's delay is deterministically the base delay, not yet jittered
		// downward. Asserted directly (not just "in range") because that determinism is
		// itself a property of this delay tier worth locking in.
		for (int i = 0; i < 50; i++) {
			assertThat(BackoffPolicy.nextDelay(1).toMillis()).isEqualTo(BASE_MILLIS);
		}
	}

	@Test
	void consecutiveFailuresIncreaseTheBackoffRange() {
		// From the second failure on, the floor no longer dominates the jitter range, so
		// each successive failure's range strictly rises: [1000,2000), [2000,4000), ...
		assertRangeAcrossSamples(2, 1000, 2000);
		assertRangeAcrossSamples(3, 2000, 4000);
		assertRangeAcrossSamples(4, 4000, 8000);
	}

	@Test
	void backoffIsCappedAtTheConfiguredMaximum() {
		// Shift is itself capped (MAX_SHIFT=20 inside BackoffPolicy), so even wildly high
		// failure counts can't push the exponential term past the 5-minute ceiling.
		for (int consecutiveFailures : new int[] { 20, 25, 100, 10_000 }) {
			for (int sample = 0; sample < 20; sample++) {
				long delayMillis = BackoffPolicy.nextDelay(consecutiveFailures).toMillis();
				assertThat(delayMillis).isLessThanOrEqualTo(MAX_MILLIS);
				assertThat(delayMillis).isGreaterThanOrEqualTo(MAX_MILLIS / 2);
			}
		}
	}

	@Test
	void jitterNeverProducesADelayBelowTheBaseDelay() {
		for (int consecutiveFailures = 1; consecutiveFailures <= 30; consecutiveFailures++) {
			for (int sample = 0; sample < 20; sample++) {
				assertThat(BackoffPolicy.nextDelay(consecutiveFailures).toMillis()).isGreaterThanOrEqualTo(BASE_MILLIS);
			}
		}
	}

	@Test
	void aNonPositiveFailureCountIsTreatedTheSameAsTheFirstFailure() {
		// scheduleNext only ever calls this with consecutiveFailures>=1 (the counter is
		// incremented before nextDelay is consulted), but the shift computation clamps
		// consecutiveFailures-1 at 0, so 0/negative inputs degrade to the same base delay
		// rather than a negative shift or an ArithmeticException.
		assertThat(BackoffPolicy.nextDelay(0).toMillis()).isEqualTo(BASE_MILLIS);
		assertThat(BackoffPolicy.nextDelay(-5).toMillis()).isEqualTo(BASE_MILLIS);
	}

	private static void assertRangeAcrossSamples(int consecutiveFailures, long expectedFloor, long expectedCeiling) {
		for (int sample = 0; sample < 30; sample++) {
			long delayMillis = BackoffPolicy.nextDelay(consecutiveFailures).toMillis();
			assertThat(delayMillis).isBetween(expectedFloor, expectedCeiling);
		}
	}
}
