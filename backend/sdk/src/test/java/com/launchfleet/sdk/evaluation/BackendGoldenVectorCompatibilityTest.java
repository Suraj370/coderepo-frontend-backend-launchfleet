package com.launchfleet.sdk.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.launchfleet.sdk.api.EvaluationContext;
import com.launchfleet.sdk.api.EvaluationDetail;
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
 * Runs shared-test-fixtures/evaluation-golden-vectors.json - the same file, unmodified -
 * through the production FlagEvaluator. The backend's EvaluationGoldenVectorTest runs the
 * identical scenarios through a reference evaluator built from backend domain objects
 * (Condition/TargetingRule/Segment/Rollout/RolloutAssigner/FeatureFlagConfig). Neither
 * test imports the other module: this one only depends on the SDK's own production
 * classes plus Jackson (already a runtime dependency of this module). Both suites
 * passing against one shared file is what proves backend/SDK evaluation compatibility -
 * this file alone only proves the SDK agrees with itself.
 */
class BackendGoldenVectorCompatibilityTest {

	@ParameterizedTest(name = "{0}")
	@MethodSource("scenarios")
	void sdkEvaluatorMatchesTheGoldenVector(String name, FixtureScenario scenario) {
		ConfigurationSnapshot snapshot = toSnapshot(scenario);
		EvaluationContext context = EvaluationContext.of(scenario.context().userKey(), scenario.context().attributes());

		EvaluationDetail detail = FlagEvaluator.evaluate(snapshot, scenario.flagKey(), context);

		assertThat(detail.variantKey()).as(scenario.name()).isEqualTo(scenario.expectedVariantKey());
	}

	static Stream<Arguments> scenarios() throws IOException {
		FixtureFile fixture = loadFixture();

		return fixture.scenarios().stream().map(scenario -> Arguments.of(scenario.name(), scenario));
	}

	private static FixtureFile loadFixture() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		try (InputStream stream = BackendGoldenVectorCompatibilityTest.class.getClassLoader()
				.getResourceAsStream("evaluation-golden-vectors.json")) {
			if (stream == null) {
				throw new IllegalStateException("evaluation-golden-vectors.json not found on the test classpath.");
			}
			return mapper.readValue(stream, FixtureFile.class);
		}
	}

	// --- fixture -> real wire DTOs -> ConfigurationSnapshot.fromWire, exactly the assembly path a real fetch takes ---

	private static ConfigurationSnapshot toSnapshot(FixtureScenario scenario) {
		SdkFlagWire flagWire = toFlagWire(scenario.flagKey(), scenario.flag());
		List<SdkSegmentWire> segmentWires = scenario.segments().stream()
				.map(BackendGoldenVectorCompatibilityTest::toSegmentWire).toList();

		SdkConfigurationWire wire = new SdkConfigurationWire(scenario.environmentId(), scenario.environmentId(), "v1",
				Instant.now().toString(), List.of(flagWire), segmentWires);

		return ConfigurationSnapshot.fromWire(wire, Instant.now());
	}

	private static SdkFlagWire toFlagWire(String flagKey, FixtureFlag flag) {
		SdkVariantWire defaultVariant = toVariantWire(flag.defaultVariant());
		List<SdkVariantWire> variants = flag.variants().stream()
				.map(BackendGoldenVectorCompatibilityTest::toVariantWire).toList();
		List<SdkTargetingRuleWire> rules = flag.targetingRules().stream()
				.map(BackendGoldenVectorCompatibilityTest::toRuleWire).toList();
		SdkRolloutWire rollout = flag.rollout() == null ? null
				: new SdkRolloutWire(flag.rollout().allocations().stream()
						.map(allocation -> new SdkAllocationWire(allocation.variantId(), allocation.percentage()))
						.toList());

		return new SdkFlagWire(flagKey, flag.enabled(), defaultVariant, variants, rules, rollout, 1);
	}

	private static SdkVariantWire toVariantWire(FixtureVariant variant) {
		return new SdkVariantWire(variant.id(), variant.key(), variant.key(), variant.value());
	}

	private static SdkTargetingRuleWire toRuleWire(FixtureRule rule) {
		List<SdkConditionWire> conditions = rule.conditions().stream()
				.map(BackendGoldenVectorCompatibilityTest::toConditionWire).toList();

		return new SdkTargetingRuleWire(rule.id(), rule.priority(), conditions, rule.variantId());
	}

	private static SdkConditionWire toConditionWire(FixtureCondition condition) {
		return new SdkConditionWire(condition.type(), condition.attribute(), condition.operator(), condition.values());
	}

	private static SdkSegmentWire toSegmentWire(FixtureSegment segment) {
		List<SdkConditionWire> conditions = segment.conditions().stream()
				.map(BackendGoldenVectorCompatibilityTest::toConditionWire).toList();

		return new SdkSegmentWire(segment.id(), segment.key(), conditions);
	}

	// --- fixture DTOs: deserialized straight off evaluation-golden-vectors.json, test-only ---

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record FixtureFile(List<FixtureScenario> scenarios) {
	}

	private record FixtureScenario(String name, String environmentId, String flagKey, List<FixtureSegment> segments,
			FixtureFlag flag, FixtureContext context, String expectedVariantKey) {
	}

	private record FixtureSegment(String id, String key, List<FixtureCondition> conditions) {
	}

	private record FixtureFlag(boolean enabled, FixtureVariant defaultVariant, List<FixtureVariant> variants,
			List<FixtureRule> targetingRules, FixtureRollout rollout) {
	}

	private record FixtureVariant(String id, String key, Object value) {
	}

	private record FixtureRule(String id, int priority, List<FixtureCondition> conditions, String variantId) {
	}

	private record FixtureCondition(String type, String attribute, String operator, List<String> values) {
	}

	private record FixtureRollout(List<FixtureAllocation> allocations) {
	}

	private record FixtureAllocation(String variantId, int percentage) {
	}

	private record FixtureContext(String userKey, Map<String, Object> attributes) {
	}
}
