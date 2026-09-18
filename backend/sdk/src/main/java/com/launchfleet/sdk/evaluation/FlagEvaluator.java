package com.launchfleet.sdk.evaluation;

import java.util.List;
import java.util.Optional;

import com.launchfleet.sdk.api.EvaluationContext;
import com.launchfleet.sdk.api.EvaluationDetail;
import com.launchfleet.sdk.api.EvaluationReason;
import com.launchfleet.sdk.configuration.ConfigurationSnapshot;
import com.launchfleet.sdk.configuration.SnapshotCondition;
import com.launchfleet.sdk.configuration.SnapshotFlag;
import com.launchfleet.sdk.configuration.SnapshotRollout;
import com.launchfleet.sdk.configuration.SnapshotSegment;
import com.launchfleet.sdk.configuration.SnapshotTargetingRule;
import com.launchfleet.sdk.configuration.SnapshotVariant;

/**
 * A pure evaluator: (ConfigurationSnapshot, flagKey, EvaluationContext) -> EvaluationDetail,
 * with no HTTP, no MongoDB, no cache dependency, and no mutable/global state - every
 * call is independent and depends only on its arguments, which is what makes the
 * result deterministic and safe to call concurrently from any number of threads
 * against the same (immutable) snapshot.
 *
 * Reproduces the locked evaluation order exactly:
 *   flag missing -> FLAG_NOT_FOUND
 *   disabled -> defaultVariant / DISABLED
 *   rules sorted by (priority, id), first AND-combined match -> TARGET_MATCH
 *   no rule matched, rollout configured, blank/missing userKey -> defaultVariant / NO_USER_KEY
 *   no rule matched, rollout configured, deterministic bucket assigned -> ROLLOUT
 *   otherwise -> defaultVariant / DEFAULT
 *
 * A rule/rollout that resolves to a variant id no longer present on the flag (malformed
 * upstream data) is treated as "this match doesn't apply" rather than thrown - see
 * SnapshotFlag.variantById - so evaluation always completes and returns something usable.
 */
public final class FlagEvaluator {

	private FlagEvaluator() {
	}

	public static EvaluationDetail evaluate(ConfigurationSnapshot snapshot, String flagKey, EvaluationContext context) {
		Optional<SnapshotFlag> maybeFlag = snapshot.flag(flagKey);
		if (maybeFlag.isEmpty()) {
			return EvaluationDetail.flagNotFound(snapshot.version());
		}

		SnapshotFlag flag = maybeFlag.get();

		if (!flag.enabled()) {
			return EvaluationDetail.of(flag.defaultVariant(), EvaluationReason.DISABLED, snapshot.version());
		}

		for (SnapshotTargetingRule rule : flag.targetingRules()) {
			if (matchesAll(rule.conditions(), context, snapshot)) {
				Optional<SnapshotVariant> variant = flag.variantById(rule.variantId());
				if (variant.isPresent()) {
					return EvaluationDetail.of(variant.get(), EvaluationReason.TARGET_MATCH, snapshot.version());
				}
				// Rule matched but its variant no longer exists on the flag - keep evaluating
				// subsequent rules/rollout/default rather than treating this as a match.
			}
		}

		SnapshotRollout rollout = flag.rollout();
		if (rollout != null) {
			String userKey = context.userKey();
			if (userKey == null || userKey.isBlank()) {
				return EvaluationDetail.of(flag.defaultVariant(), EvaluationReason.NO_USER_KEY, snapshot.version());
			}

			Optional<String> assignedVariantId = RolloutHasher.assign(snapshot.environmentId(), flagKey, userKey,
					rollout);
			if (assignedVariantId.isPresent()) {
				Optional<SnapshotVariant> variant = flag.variantById(assignedVariantId.get());
				if (variant.isPresent()) {
					return EvaluationDetail.of(variant.get(), EvaluationReason.ROLLOUT, snapshot.version());
				}
			}
		}

		return EvaluationDetail.of(flag.defaultVariant(), EvaluationReason.DEFAULT, snapshot.version());
	}

	private static boolean matchesAll(List<SnapshotCondition> conditions, EvaluationContext context,
			ConfigurationSnapshot snapshot) {
		for (SnapshotCondition condition : conditions) {
			if (!matches(condition, context, snapshot)) {
				return false;
			}
		}

		return true;
	}

	private static boolean matches(SnapshotCondition condition, EvaluationContext context,
			ConfigurationSnapshot snapshot) {
		return switch (condition.type()) {
			case ATTRIBUTE -> matchesAttribute(condition, context);
			case USER_KEY -> matchesUserKey(condition, context);
			case SEGMENT_MATCH -> matchesSegment(condition, context, snapshot);
		};
	}

	/** A null/absent attribute never matches - it is not coerced to any literal value. */
	private static boolean matchesAttribute(SnapshotCondition condition, EvaluationContext context) {
		Object raw = context.attribute(condition.attribute());
		if (raw == null) {
			return false;
		}

		return matchesValue(String.valueOf(raw), condition);
	}

	private static boolean matchesUserKey(SnapshotCondition condition, EvaluationContext context) {
		String userKey = context.userKey();
		if (userKey == null || userKey.isBlank()) {
			return false;
		}

		return matchesValue(userKey, condition);
	}

	/** A segment id that isn't in the snapshot (stale/missing data) fails closed - it never matches. */
	private static boolean matchesSegment(SnapshotCondition condition, EvaluationContext context,
			ConfigurationSnapshot snapshot) {
		String segmentId = condition.values().get(0);
		Optional<SnapshotSegment> segment = snapshot.segment(segmentId);
		if (segment.isEmpty()) {
			return false;
		}

		// A segment's own conditions are AND-combined, the same rule TargetingRule uses -
		// see SnapshotSegment's javadoc for why this is the one coherent combination rule.
		return matchesAll(segment.get().conditions(), context, snapshot);
	}

	private static boolean matchesValue(String actual, SnapshotCondition condition) {
		return switch (condition.operator()) {
			case EQUALS -> actual.equals(condition.values().get(0));
			case IN -> condition.values().contains(actual);
		};
	}
}
