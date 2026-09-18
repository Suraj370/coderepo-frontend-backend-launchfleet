package com.launchfleet.sdk.evaluation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import com.launchfleet.sdk.configuration.SnapshotAllocation;
import com.launchfleet.sdk.configuration.SnapshotRollout;

/**
 * A LITERAL port of the backend's RolloutAssigner - same hash input format
 * ("environmentId:flagKey:userKey"), same SHA-256 primitive, same first-four-bytes-as-
 * unsigned-32-bit-int extraction, same mod 10000, same cumulative-range walk in
 * ascending-variantId order (see SnapshotRollout.of, which pre-sorts allocations the
 * same way Rollout.sortedByVariantId() does server-side). This is intentional
 * duplication, not a second hashing implementation: the SDK cannot depend on the
 * backend module (no Spring/Mongo dependency allowed - see the sdk module's
 * build.gradle), so this is the only way for the two processes to agree byte-for-byte.
 * Compatibility is proven, not assumed - see RolloutHasherCompatibilityTest, which
 * runs the exact fixtures from the backend's RolloutAssignerTest against this class.
 *
 * Deliberately has no I/O, no mutable state, no dependency on FlagEvaluator/Segment
 * matching, and no randomness, mirroring RolloutAssigner's own design.
 */
public final class RolloutHasher {

	private static final int TOTAL_BASIS_POINTS = 10000;

	private RolloutHasher() {
	}

	/** Empty when userKey is missing/blank - callers fall through to defaultVariant, exactly like the server. */
	public static Optional<String> assign(String environmentId, String flagKey, String userKey,
			SnapshotRollout rollout) {
		if (userKey == null || userKey.isBlank()) {
			return Optional.empty();
		}

		int bucket = bucketFor(environmentId, flagKey, userKey);

		return variantForBucket(bucket, rollout);
	}

	static int bucketFor(String environmentId, String flagKey, String userKey) {
		String input = environmentId + ":" + flagKey + ":" + userKey;
		byte[] digest = sha256(input);

		int unsignedHashInt = ((digest[0] & 0xFF) << 24) | ((digest[1] & 0xFF) << 16) | ((digest[2] & 0xFF) << 8)
				| (digest[3] & 0xFF);

		return Math.floorMod(unsignedHashInt, TOTAL_BASIS_POINTS);
	}

	static Optional<String> variantForBucket(int bucket, SnapshotRollout rollout) {
		int cumulative = 0;

		for (SnapshotAllocation allocation : rollout.allocations()) {
			cumulative += allocation.percentage();
			if (bucket < cumulative) {
				return Optional.of(allocation.variantId());
			}
		}

		// Unreachable when the rollout's allocations sum to exactly 10000 (guaranteed by
		// the server-side Rollout invariant this payload was built from) and bucket is
		// always < 10000 - a defensive, fail-closed fallback rather than an exception if a
		// malformed payload's allocations don't actually sum to 10000.
		return Optional.empty();
	}

	private static byte[] sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");

			return digest.digest(input.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}
}
