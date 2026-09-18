package com.launchfleet.sdk.internal;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with a floor, a cap, and jitter, for background refresh retries
 * only - never used to delay an evaluation, which never touches the network at all.
 * Not part of the SDK's public API (internal/): callers configure the base refresh
 * interval (SdkOptions), not backoff mechanics.
 */
public final class BackoffPolicy {

	private static final Duration BASE_DELAY = Duration.ofSeconds(1);

	private static final Duration MAX_DELAY = Duration.ofMinutes(5);

	private static final int MAX_SHIFT = 20;

	private BackoffPolicy() {
	}

	/** consecutiveFailures is 1 for the first failure since the last success. */
	public static Duration nextDelay(int consecutiveFailures) {
		long baseMillis = BASE_DELAY.toMillis();
		int shift = Math.min(Math.max(consecutiveFailures - 1, 0), MAX_SHIFT);
		long exponential = baseMillis * (1L << shift);
		long capped = Math.min(exponential, MAX_DELAY.toMillis());
		long jittered = ThreadLocalRandom.current().nextLong(capped / 2, capped + 1);

		return Duration.ofMillis(Math.max(jittered, baseMillis));
	}
}
