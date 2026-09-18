package com.launchfleet.backend.featureflags.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Runs shared-test-fixtures/evaluation-golden-vectors.json (see its own description field)
 * against a reference evaluator built from real backend domain objects - Condition,
 * TargetingRule, Segment, Rollout, Allocation, RolloutAssigner, FeatureFlagConfig - the
 * same pieces RolloutEvaluationOrderTest composes, extended with full condition/segment
 * matching. That extension has no home anywhere in main/ (see this class's Javadoc on
 * referenceEvaluate): the backend has never implemented a general evaluation engine or
 * endpoint, by design - "evaluate a flag for a user" is a Phase 5+ SDK-side concern (see
 * SdkConfigurationResource's Javadoc), and this repo's only prior evaluation-order proof
 * (RolloutEvaluationOrderTest) inlines just enough USER_KEY matching to prove ordering,
 * explicitly deferring full matching to "Phase 5+".
 *
 * sdk's BackendGoldenVectorCompatibilityTest runs the exact same fixture through the
 * production FlagEvaluator. Both tests passing on the same file is the actual proof of
 * compatibility - either one passing alone proves nothing.
 */
class EvaluationGoldenVectorTest {

	private static final String PROJECT_ID = "project-1";

	private static final String ACTOR = "golden-vector-test";

	@ParameterizedTest(name = "{0}")
	@MethodSource("scenarios")
	void backendReferenceEvaluatorMatchesTheGoldenVector(String name, FixtureScenario scenario) {
		FeatureFlagConfig config = toConfig(scenario);
		List<Segment> segments = scenario.segments().stream().map(EvaluationGoldenVectorTest::toSegment).toList();

		String variantId = referenceEvaluate(config, segments, scenario.flagKey(), scenario.context());
		String variantKey = keyOf(scenario.flag(), variantId);

		assertThat(variantKey).as(scenario.name()).isEqualTo(scenario.expectedVariantKey());
	}

	static Stream<org.junit.jupiter.params.provider.Arguments> scenarios() throws IOException {
		FixtureFile fixture = loadFixture();

		return fixture.scenarios().stream()
				.map(scenario -> org.junit.jupiter.params.provider.Arguments.of(scenario.name(), scenario));
	}

	private static FixtureFile loadFixture() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		try (InputStream stream = EvaluationGoldenVectorTest.class.getClassLoader()
				.getResourceAsStream("evaluation-golden-vectors.json")) {
			if (stream == null) {
				throw new IllegalStateException("evaluation-golden-vectors.json not found on the test classpath.");
			}
			return mapper.readValue(stream, FixtureFile.class);
		}
	}

	// --- reference evaluator: the one piece this fixture exists to prove agrees with the SDK ---

	/**
	 * The locked evaluation order (see RolloutEvaluationOrderTest), extended with full
	 * condition/segment matching per Condition's own documented semantics. Deliberately
	 * test-only: production backend code has no caller for this (see class Javadoc).
	 */
	private static String referenceEvaluate(FeatureFlagConfig config, List<Segment> segments, String flagKey,
			FixtureContext context) {
		if (!config.isEnabled()) {
			return config.getDefaultVariantId();
		}

		Map<String, Segment> segmentsById = segments.stream()
				.collect(java.util.stream.Collectors.toMap(Segment::getId, segment -> segment));

		for (TargetingRule rule : config.getTargetingRules()) {
			if (rule.getConditions().stream().allMatch(condition -> matches(condition, context, segmentsById))) {
				return rule.getVariantId();
			}
		}

		Rollout rollout = config.getRollout();
		if (rollout != null) {
			Optional<String> assigned = RolloutAssigner.assign(config.getEnvironmentId(), flagKey,
					context.userKey(), rollout);
			if (assigned.isPresent()) {
				return assigned.get();
			}
		}

		return config.getDefaultVariantId();
	}

	private static boolean matches(Condition condition, FixtureContext context, Map<String, Segment> segmentsById) {
		return switch (condition.type()) {
			case USER_KEY -> {
				String userKey = context.userKey();
				yield userKey != null && !userKey.isBlank() && matchesValue(userKey, condition);
			}
			case ATTRIBUTE -> {
				Object raw = context.attributes().get(condition.attribute());
				yield raw != null && matchesValue(String.valueOf(raw), condition);
			}
			case SEGMENT_MATCH -> {
				Segment segment = segmentsById.get(condition.values().get(0));
				yield segment != null
						&& segment.getConditions().stream().allMatch(inner -> matches(inner, context, segmentsById));
			}
		};
	}

	private static boolean matchesValue(String actual, Condition condition) {
		return switch (condition.operator()) {
			case EQUALS -> actual.equals(condition.values().get(0));
			case IN -> condition.values().contains(actual);
		};
	}

	// --- fixture -> real domain objects ---

	private static FeatureFlagConfig toConfig(FixtureScenario scenario) {
		FixtureFlag flag = scenario.flag();

		List<TargetingRule> rules = flag.targetingRules().stream()
				.map(rule -> TargetingRule.create(rule.priority(),
						rule.conditions().stream().map(EvaluationGoldenVectorTest::toCondition).toList(),
						rule.variantId()))
				.sorted(java.util.Comparator.comparingInt(TargetingRule::getPriority).thenComparing(TargetingRule::getId))
				.toList();

		Rollout rollout = flag.rollout() == null ? null
				: new Rollout(flag.rollout().allocations().stream()
						.map(allocation -> new Allocation(allocation.variantId(), allocation.percentage())).toList());

		Instant now = Instant.now();
		return FeatureFlagConfig.reconstitute("config-1", "flag-1", scenario.environmentId(), PROJECT_ID,
				flag.enabled(), flag.defaultVariant().id(), rules, rollout, 1, ACTOR, now, now);
	}

	private static Segment toSegment(FixtureSegment segment) {
		List<Condition> conditions = segment.conditions().stream().map(EvaluationGoldenVectorTest::toCondition).toList();
		Instant now = Instant.now();

		return Segment.reconstitute(segment.id(), PROJECT_ID, segment.key(), segment.key(), SegmentStatus.ACTIVE,
				conditions, ACTOR, now, ACTOR, now);
	}

	private static Condition toCondition(FixtureCondition condition) {
		return new Condition(ConditionType.valueOf(condition.type()), condition.attribute(),
				ConditionOperator.valueOf(condition.operator()), condition.values());
	}

	private static String keyOf(FixtureFlag flag, String variantId) {
		return flag.variants().stream().filter(variant -> variant.id().equals(variantId)).findFirst()
				.map(FixtureVariant::key)
				.orElseThrow(() -> new IllegalStateException("No variant with id " + variantId + " on this flag."));
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
