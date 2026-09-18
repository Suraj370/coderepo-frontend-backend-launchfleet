package com.launchfleet.backend.featureflags.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * A narrow, pure rollout-assignment primitive - not a general evaluation engine (see
 * the featureflags module's evaluation-order documentation for the full flow this
 * plugs into: disabled check and targeting-rule matching both happen before this is
 * ever called). Deliberately has no I/O, no persistence, no mutable state, no
 * dependency on TargetingRule or Segment, and no randomness - the same
 * (environmentId, flagKey, userKey, rollout) always produces the same result.
 *
 * Hash input is "environmentId:flagKey:userKey" (SHA-256, via the JDK's
 * MessageDigest - no third-party hashing dependency), so bucket assignment is
 * independent per environment: the same user's staging and production buckets are
 * uncorrelated. The first four bytes of the digest, read as an unsigned 32-bit
 * integer, are reduced mod 10000 to land in the same [0, 10000) basis-point space
 * Allocation.percentage uses. Allocations are always consulted in
 * Rollout.sortedByVariantId() order (lexicographic by variantId) when building
 * cumulative ranges, so which variant a bucket lands in never depends on Mongo/list
 * insertion order.
 */
public final class RolloutAssigner {

	private RolloutAssigner() {
	}

	/**
	 * Returns the assigned variantId, or empty if this user cannot be deterministically
	 * bucketed (missing or blank userKey) - callers fall through to defaultVariantId in
	 * that case, exactly as they do when no rollout is configured at all. Does not
	 * invent anonymous-user semantics: a blank userKey is never hashed as a literal
	 * empty-string identity.
	 */
	public static Optional<String> assign(String environmentId, String flagKey, String userKey, Rollout rollout) {
		if (userKey == null || userKey.isBlank()) {
			return Optional.empty();
		}

		int bucket = bucketFor(environmentId, flagKey, userKey);

		return variantForBucket(bucket, rollout);
	}

	/**
	 * The deterministic [0, 10000) bucket for one (environmentId, flagKey, userKey)
	 * triple - exposed package-private so hashing behavior itself (independence across
	 * environments/users, stability across repeated calls) can be tested directly,
	 * separately from the cumulative-range mapping in variantForBucket.
	 */
	static int bucketFor(String environmentId, String flagKey, String userKey) {
		String input = environmentId + ":" + flagKey + ":" + userKey;
		byte[] digest = sha256(input);

		int unsignedHashInt = ((digest[0] & 0xFF) << 24) | ((digest[1] & 0xFF) << 16) | ((digest[2] & 0xFF) << 8)
				| (digest[3] & 0xFF);

		return Math.floorMod(unsignedHashInt, 10000);
	}

	/**
	 * Maps an already-computed bucket to the variant whose cumulative basis-point range
	 * contains it, walking allocations in Rollout.sortedByVariantId() order. Exposed
	 * package-private so exact boundary behavior (bucket 0, bucket 9999, and each
	 * cumulative range edge) can be asserted with synthetic bucket values instead of
	 * relying on real SHA-256 output happening to land there.
	 */
	static Optional<String> variantForBucket(int bucket, Rollout rollout) {
		int cumulative = 0;

		for (Allocation allocation : rollout.sortedByVariantId()) {
			cumulative += allocation.percentage();
			if (bucket < cumulative) {
				return Optional.of(allocation.variantId());
			}
		}

		// Unreachable when Rollout's invariants hold (allocations always sum to exactly
		// 10000 and bucket is always < 10000), but a defensive, fail-closed fallback
		// rather than an unchecked exception if that invariant is ever violated.
		return Optional.empty();
	}

	private static byte[] sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return digest.digest(input.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException exception) {
			// SHA-256 is guaranteed available on every JDK platform (see
			// MessageDigest's javadoc); this branch cannot occur in practice.
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}
}
