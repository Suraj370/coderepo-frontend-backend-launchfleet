package com.launchfleet.sdk.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.launchfleet.sdk.api.EvaluationContext;
import com.launchfleet.sdk.api.EvaluationDetail;
import com.launchfleet.sdk.api.EvaluationReason;
import com.launchfleet.sdk.configuration.ConditionOperator;
import com.launchfleet.sdk.configuration.ConditionType;
import com.launchfleet.sdk.configuration.ConfigurationSnapshot;
import com.launchfleet.sdk.transport.SdkAllocationWire;
import com.launchfleet.sdk.transport.SdkConditionWire;
import com.launchfleet.sdk.transport.SdkConfigurationWire;
import com.launchfleet.sdk.transport.SdkFlagWire;
import com.launchfleet.sdk.transport.SdkRolloutWire;
import com.launchfleet.sdk.transport.SdkSegmentWire;
import com.launchfleet.sdk.transport.SdkTargetingRuleWire;
import com.launchfleet.sdk.transport.SdkVariantWire;

/**
 * Exercises FlagEvaluator against the full Phase 1-4 evaluation order: disabled ->
 * targeting (priority, AND-combination, segments) -> rollout -> default, plus
 * flag-not-found/missing-userKey edge cases. Snapshots are built through
 * ConfigurationSnapshot.fromWire (never constructed by hand) so these tests also
 * exercise the exact assembly path a real fetch would go through.
 */
class FlagEvaluatorTest {

	private static final String ENVIRONMENT_ID = "env-1";

	private static final SdkVariantWire OFF = new SdkVariantWire("v-off", "false", "Off", Boolean.FALSE);

	private static final SdkVariantWire ON = new SdkVariantWire("v-on", "true", "On", Boolean.TRUE);

	@Test
	void disabledFlagReturnsDefaultVariantWithDisabledReason() {
		ConfigurationSnapshot snapshot = snapshotOf(flag("checkout", false, OFF, List.of(OFF, ON), List.of(), null));

		EvaluationDetail detail = FlagEvaluator.evaluate(snapshot, "checkout", EvaluationContext.of("alice"));

		assertThat(detail.reason()).isEqualTo(EvaluationReason.DISABLED);
		assertThat(detail.value()).isEqualTo(Boolean.FALSE);
	}

	@Test
	void unknownFlagReturnsFlagNotFound() {
		ConfigurationSnapshot snapshot = snapshotOf();

		EvaluationDetail detail = FlagEvaluator.evaluate(snapshot, "does-not-exist", EvaluationContext.of("alice"));

		assertThat(detail.reason()).isEqualTo(EvaluationReason.FLAG_NOT_FOUND);
		assertThat(detail.hasVariant()).isFalse();
	}

	@Test
	void aMatchingTargetingRuleTakesPrecedenceOverRollout() {
		SdkTargetingRuleWire rule = rule("r1", 1, ON.id(),
				condition(ConditionType.USER_KEY, null, ConditionOperator.EQUALS, "alice"));
		SdkRolloutWire rollout = new SdkRolloutWire(List.of(new SdkAllocationWire(ON.id(), 10000)));
		ConfigurationSnapshot snapshot = snapshotOf(flag("checkout", true, OFF, List.of(OFF, ON), List.of(rule), rollout));

		EvaluationDetail detail = FlagEvaluator.evaluate(snapshot, "checkout", EvaluationContext.of("alice"));

		assertThat(detail.reason()).isEqualTo(EvaluationReason.TARGET_MATCH);
		assertThat(detail.value()).isEqualTo(Boolean.TRUE);
	}

	@Test
	void lowerPriorityNumberIsEvaluatedFirstAndWins() {
		SdkTargetingRuleWire lowPriorityWinner = rule("r-low", 0, ON.id(),
				condition(ConditionType.USER_KEY, null, ConditionOperator.EQUALS, "alice"));
		SdkTargetingRuleWire higherPriorityLoser = rule("r-high", 1, OFF.id(),
				condition(ConditionType.USER_KEY, null, ConditionOperator.EQUALS, "alice"));
		// Constructed out of priority order on purpose - the evaluator must sort, not trust wire order.
		ConfigurationSnapshot snapshot = snapshotOf(
				flag("checkout", true, OFF, List.of(OFF, ON), List.of(higherPriorityLoser, lowPriorityWinner), null));

		EvaluationDetail detail = FlagEvaluator.evaluate(snapshot, "checkout", EvaluationContext.of("alice"));

		assertThat(detail.value()).isEqualTo(Boolean.TRUE);
	}

	@Test
	void conditionsWithinARuleAreAndCombined() {
		SdkTargetingRuleWire rule = rule("r1", 0, ON.id(),
				condition(ConditionType.ATTRIBUTE, "plan", ConditionOperator.EQUALS, "gold"),
				condition(ConditionType.USER_KEY, null, ConditionOperator.EQUALS, "alice"));
		ConfigurationSnapshot snapshot = snapshotOf(flag("checkout", true, OFF, List.of(OFF, ON), List.of(rule), null));

		EvaluationDetail matchesBoth = FlagEvaluator.evaluate(snapshot, "checkout",
				EvaluationContext.of("alice", Map.of("plan", "gold")));
		EvaluationDetail matchesOnlyOne = FlagEvaluator.evaluate(snapshot, "checkout",
				EvaluationContext.of("alice", Map.of("plan", "silver")));

		assertThat(matchesBoth.reason()).isEqualTo(EvaluationReason.TARGET_MATCH);
		assertThat(matchesOnlyOne.reason()).isEqualTo(EvaluationReason.DEFAULT);
	}

	@Test
	void inOperatorMatchesAnyListedValue() {
		SdkTargetingRuleWire rule = rule("r1", 0, ON.id(),
				condition(ConditionType.ATTRIBUTE, "plan", ConditionOperator.IN, "gold", "platinum"));
		ConfigurationSnapshot snapshot = snapshotOf(flag("checkout", true, OFF, List.of(OFF, ON), List.of(rule), null));

		assertThat(FlagEvaluator
				.evaluate(snapshot, "checkout", EvaluationContext.of("alice", Map.of("plan", "platinum"))).value())
				.isEqualTo(Boolean.TRUE);
		assertThat(
				FlagEvaluator.evaluate(snapshot, "checkout", EvaluationContext.of("alice", Map.of("plan", "bronze")))
						.reason()).isEqualTo(EvaluationReason.DEFAULT);
	}

	@Test
	void aNullOrAbsentAttributeNeverMatches() {
		SdkTargetingRuleWire rule = rule("r1", 0, ON.id(),
				condition(ConditionType.ATTRIBUTE, "plan", ConditionOperator.EQUALS, "gold"));
		ConfigurationSnapshot snapshot = snapshotOf(flag("checkout", true, OFF, List.of(OFF, ON), List.of(rule), null));

		EvaluationDetail detail = FlagEvaluator.evaluate(snapshot, "checkout", EvaluationContext.of("alice"));

		assertThat(detail.reason()).isEqualTo(EvaluationReason.DEFAULT);
	}

	@Test
	void segmentMatchResolvesFromTheSnapshotsSegmentMap() {
		SdkSegmentWire segment = new SdkSegmentWire("seg-1", "beta-users",
				List.of(toWire(condition(ConditionType.USER_KEY, null, ConditionOperator.IN, "alice", "bob"))));
		SdkTargetingRuleWire rule = rule("r1", 0, ON.id(),
				condition(ConditionType.SEGMENT_MATCH, null, ConditionOperator.EQUALS, "seg-1"));
		ConfigurationSnapshot snapshot = snapshotOf(List.of(segment),
				flag("checkout", true, OFF, List.of(OFF, ON), List.of(rule), null));

		assertThat(FlagEvaluator.evaluate(snapshot, "checkout", EvaluationContext.of("alice")).value())
				.isEqualTo(Boolean.TRUE);
		assertThat(FlagEvaluator.evaluate(snapshot, "checkout", EvaluationContext.of("carol")).reason())
				.isEqualTo(EvaluationReason.DEFAULT);
	}

	@Test
	void aUserNotMatchedByAnyRuleFallsThroughToRollout() {
		SdkTargetingRuleWire rule = rule("r1", 0, ON.id(),
				condition(ConditionType.USER_KEY, null, ConditionOperator.EQUALS, "alice"));
		SdkRolloutWire rollout = new SdkRolloutWire(List.of(new SdkAllocationWire(ON.id(), 10000)));
		ConfigurationSnapshot snapshot = snapshotOf(flag("checkout", true, OFF, List.of(OFF, ON), List.of(rule), rollout));

		EvaluationDetail detail = FlagEvaluator.evaluate(snapshot, "checkout", EvaluationContext.of("bob"));

		assertThat(detail.reason()).isEqualTo(EvaluationReason.ROLLOUT);
		assertThat(detail.value()).isEqualTo(Boolean.TRUE);
	}

	@Test
	void noRolloutConfiguredFallsThroughToDefaultVariant() {
		ConfigurationSnapshot snapshot = snapshotOf(flag("checkout", true, OFF, List.of(OFF, ON), List.of(), null));

		EvaluationDetail detail = FlagEvaluator.evaluate(snapshot, "checkout", EvaluationContext.of("bob"));

		assertThat(detail.reason()).isEqualTo(EvaluationReason.DEFAULT);
	}

	@Test
	void rolloutConfiguredButUserKeyMissingReturnsNoUserKeyReasonWithDefaultVariant() {
		SdkRolloutWire rollout = new SdkRolloutWire(List.of(new SdkAllocationWire(ON.id(), 10000)));
		ConfigurationSnapshot snapshot = snapshotOf(flag("checkout", true, OFF, List.of(OFF, ON), List.of(), rollout));

		EvaluationDetail missing = FlagEvaluator.evaluate(snapshot, "checkout", EvaluationContext.of(null));
		EvaluationDetail blank = FlagEvaluator.evaluate(snapshot, "checkout", EvaluationContext.of("   "));

		assertThat(missing.reason()).isEqualTo(EvaluationReason.NO_USER_KEY);
		assertThat(missing.value()).isEqualTo(Boolean.FALSE);
		assertThat(blank.reason()).isEqualTo(EvaluationReason.NO_USER_KEY);
	}

	@Test
	void configurationVersionIsCarriedOnEveryResult() {
		ConfigurationSnapshot snapshot = snapshotOf(flag("checkout", false, OFF, List.of(OFF, ON), List.of(), null));

		EvaluationDetail detail = FlagEvaluator.evaluate(snapshot, "checkout", EvaluationContext.of("alice"));

		assertThat(detail.configurationVersion()).isEqualTo(snapshot.version());
	}

	// --- fixture builders ---

	private static SdkTargetingRuleWire rule(String id, int priority, String variantId, SdkConditionWireBuilder... conditions) {
		List<SdkConditionWire> wires = List.of(conditions).stream().map(FlagEvaluatorTest::toWire).toList();

		return new SdkTargetingRuleWire(id, priority, wires, variantId);
	}

	private static SdkConditionWireBuilder condition(ConditionType type, String attribute, ConditionOperator operator,
			String... values) {
		return new SdkConditionWireBuilder(type, attribute, operator, List.of(values));
	}

	private static SdkConditionWire toWire(SdkConditionWireBuilder builder) {
		return new SdkConditionWire(builder.type.name(), builder.attribute, builder.operator.name(), builder.values);
	}

	private record SdkConditionWireBuilder(ConditionType type, String attribute, ConditionOperator operator,
			List<String> values) {
	}

	private static SdkFlagWire flag(String key, boolean enabled, SdkVariantWire defaultVariant,
			List<SdkVariantWire> variants, List<SdkTargetingRuleWire> rules, SdkRolloutWire rollout) {
		return new SdkFlagWire(key, enabled, defaultVariant, variants, rules, rollout, 1);
	}

	private static ConfigurationSnapshot snapshotOf(SdkFlagWire... flags) {
		return snapshotOf(List.of(), flags);
	}

	private static ConfigurationSnapshot snapshotOf(List<SdkSegmentWire> segments, SdkFlagWire... flags) {
		SdkConfigurationWire wire = new SdkConfigurationWire(ENVIRONMENT_ID, "production", "v1",
				Instant.now().toString(), List.of(flags), segments);

		return ConfigurationSnapshot.fromWire(wire, Instant.now());
	}
}
