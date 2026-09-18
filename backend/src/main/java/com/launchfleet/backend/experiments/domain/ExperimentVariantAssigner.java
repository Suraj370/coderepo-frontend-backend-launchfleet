package com.launchfleet.backend.experiments.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.domain.Rollout;

/**
 * Deterministic bucket assignment for experiment variants - deliberately NOT
 * featureflags.domain.RolloutAssigner reused directly. That primitive's own
 * Javadoc scopes its hash input to "environmentId:flagKey:userKey", a
 * FeatureFlag-rollout-specific concept; passing an experimentId into its flagKey
 * parameter (as an earlier version of this code did) made that primitive's own
 * meaning ambiguous. This is a small, separate primitive whose hash input
 * clearly represents the experiment instead: "environmentId:experimentId:userKey".
 *
 * It deliberately reuses the exact same SHA-256/bucket algorithm as
 * RolloutAssigner (same digest, same 4-byte/mod-10000 bucketing, same
 * cumulative-range walk over Rollout.sortedByVariantId()) so experiment
 * bucketing is exactly as deterministic and evenly distributed as flag rollout
 * bucketing, without the two concepts sharing a dependency. Scoping the hash by
 * experimentId (not flagKey) means one experiment's bucketing is independent of
 * its own flag's rollout hashing and of any other experiment defined on the same
 * flag.
 */
public final class ExperimentVariantAssigner {

	private ExperimentVariantAssigner() {
	}

	/**
	 * Returns the assigned variantId, or empty if this user cannot be
	 * deterministically bucketed (missing or blank userKey) - callers must not
	 * persist an assignment in that case.
	 */
	public static Optional<String> assign(String environmentId, String experimentId, String userKey,
			Rollout allocation) {
		if (userKey == null || userKey.isBlank()) {
			return Optional.empty();
		}

		int bucket = bucketFor(environmentId, experimentId, userKey);

		return variantForBucket(bucket, allocation);
	}

	/** Exposed package-private so hashing behavior itself can be tested directly, mirroring RolloutAssignerTest. */
	static int bucketFor(String environmentId, String experimentId, String userKey) {
		String input = environmentId + ":" + experimentId + ":" + userKey;
		byte[] digest = sha256(input);

		int unsignedHashInt = ((digest[0] & 0xFF) << 24) | ((digest[1] & 0xFF) << 16) | ((digest[2] & 0xFF) << 8)
				| (digest[3] & 0xFF);

		return Math.floorMod(unsignedHashInt, 10000);
	}

	/** Exposed package-private so boundary behavior can be asserted with synthetic bucket values. */
	static Optional<String> variantForBucket(int bucket, Rollout allocation) {
		int cumulative = 0;

		for (Allocation entry : allocation.sortedByVariantId()) {
			cumulative += entry.percentage();
			if (bucket < cumulative) {
				return Optional.of(entry.variantId());
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
