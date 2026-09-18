package com.launchfleet.backend.featureflags.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.ConditionOperator;
import com.launchfleet.backend.featureflags.domain.ConditionType;
import com.launchfleet.backend.featureflags.domain.FlagType;
import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Application use case tests for targeting-rule add/update/remove - fake, in-memory
 * ports, no Spring context, no MongoDB.
 */
class TargetingRuleUseCasesTest {

	private final InMemoryFeatureFlagStore featureFlagStore = new InMemoryFeatureFlagStore();

	private final InMemoryFeatureFlagConfigStore featureFlagConfigStore = new InMemoryFeatureFlagConfigStore();

	private final InMemoryProjectLookup projectLookup = new InMemoryProjectLookup();

	private final InMemoryEnvironmentLookup environmentLookup = new InMemoryEnvironmentLookup();

	private final InMemorySegmentStore segmentStore = new InMemorySegmentStore();

	private FeatureFlagLookup flagLookup;

	private SegmentLookup segmentLookup;

	private CreateFeatureFlag createFeatureFlag;

	private CreateSegment createSegment;

	private RetireSegment retireSegment;

	private AddTargetingRule addTargetingRule;

	private UpdateTargetingRule updateTargetingRule;

	private RemoveTargetingRule removeTargetingRule;

	private ProjectRef project;

	private String trueVariantId;

	private String falseVariantId;

	@BeforeEach
	void setUp() {
		flagLookup = new FeatureFlagLookup(featureFlagStore, featureFlagConfigStore, projectLookup, environmentLookup);
		segmentLookup = new SegmentLookup(segmentStore, projectLookup);
		TargetingRuleValidator validator = new TargetingRuleValidator(segmentStore);

		createFeatureFlag = new CreateFeatureFlag(featureFlagStore, featureFlagConfigStore, flagLookup, new InMemoryActivityRecorder());
		createSegment = new CreateSegment(segmentStore, segmentLookup, new InMemoryActivityRecorder());
		retireSegment = new RetireSegment(segmentStore, featureFlagConfigStore, segmentLookup, new InMemoryActivityRecorder());
		addTargetingRule = new AddTargetingRule(featureFlagConfigStore, flagLookup, validator);
		updateTargetingRule = new UpdateTargetingRule(featureFlagConfigStore, flagLookup, validator);
		removeTargetingRule = new RemoveTargetingRule(featureFlagConfigStore, flagLookup);

		project = projectLookup.addProject("acme");
		environmentLookup.addEnvironment(project.id(), "production");

		FeatureFlagView flag = createFeatureFlag.execute("acme", "checkout", "Checkout", null, FlagType.BOOLEAN, null,
				"actor");
		falseVariantId = flag.flag().getVariants().get(0).id();
		trueVariantId = flag.flag().getVariants().get(1).id();
	}

	private List<Condition> namedUser(String userKey) {
		return List.of(new Condition(ConditionType.USER_KEY, null, ConditionOperator.EQUALS, List.of(userKey)));
	}

	@Test
	void addingARuleAppendsItAndBumpsVersion() {
		FeatureFlagView view = addTargetingRule.execute("acme", "checkout", "production", 1, namedUser("alice"),
				trueVariantId, "actor");

		assertThat(view.configs().get(0).getTargetingRules()).hasSize(1);
		assertThat(view.configs().get(0).getVersion()).isEqualTo(2);
	}

	@Test
	void addingARuleWithAVariantNotOnTheFlagIsRejected() {
		assertThatThrownBy(() -> addTargetingRule.execute("acme", "checkout", "production", 1, namedUser("alice"),
				"not-a-real-variant", "actor"))
				.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(400));
	}

	@Test
	void updatingARuleChangesItsFieldsAndBumpsVersionOnce() {
		FeatureFlagView created = addTargetingRule.execute("acme", "checkout", "production", 1, namedUser("alice"),
				trueVariantId, "actor");
		String ruleId = created.configs().get(0).getTargetingRules().get(0).getId();

		FeatureFlagView updated = updateTargetingRule.execute("acme", "checkout", "production", ruleId, 5,
				namedUser("bob"), falseVariantId, "editor");

		assertThat(updated.configs().get(0).getTargetingRules()).hasSize(1);
		assertThat(updated.configs().get(0).getTargetingRules().get(0).getPriority()).isEqualTo(5);
		assertThat(updated.configs().get(0).getTargetingRules().get(0).getVariantId()).isEqualTo(falseVariantId);
		assertThat(updated.configs().get(0).getVersion()).isEqualTo(3);
	}

	@Test
	void updatingAnUnknownRuleIdIsRejectedAsNotFound() {
		addTargetingRule.execute("acme", "checkout", "production", 1, namedUser("alice"), trueVariantId, "actor");

		assertThatThrownBy(() -> updateTargetingRule.execute("acme", "checkout", "production", "no-such-rule", 1,
				namedUser("alice"), trueVariantId, "actor")).isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(404));
	}

	@Test
	void removingARuleDropsItAndBumpsVersion() {
		FeatureFlagView created = addTargetingRule.execute("acme", "checkout", "production", 1, namedUser("alice"),
				trueVariantId, "actor");
		String ruleId = created.configs().get(0).getTargetingRules().get(0).getId();

		FeatureFlagView afterRemove = removeTargetingRule.execute("acme", "checkout", "production", ruleId, "actor");

		assertThat(afterRemove.configs().get(0).getTargetingRules()).isEmpty();
		assertThat(afterRemove.configs().get(0).getVersion()).isEqualTo(3);
	}

	@Test
	void addingARuleReferencingAnUnknownSegmentIsRejected() {
		Condition segmentCondition = new Condition(ConditionType.SEGMENT_MATCH, null, ConditionOperator.EQUALS,
				List.of("no-such-segment"));

		assertThatThrownBy(() -> addTargetingRule.execute("acme", "checkout", "production", 1,
				List.of(segmentCondition), trueVariantId, "actor")).isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(400));
	}

	@Test
	void addingARuleReferencingARetiredSegmentIsRejected() {
		Segment segment = createSegment.execute("acme", "beta", "Beta",
				List.of(new Condition(ConditionType.ATTRIBUTE, "plan", ConditionOperator.EQUALS, List.of("gold"))),
				"actor");
		retireSegment.execute("acme", "beta", "actor");

		Condition segmentCondition = new Condition(ConditionType.SEGMENT_MATCH, null, ConditionOperator.EQUALS,
				List.of(segment.getId()));

		assertThatThrownBy(() -> addTargetingRule.execute("acme", "checkout", "production", 1,
				List.of(segmentCondition), trueVariantId, "actor")).isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(400));
	}

	@Test
	void retiringASegmentReferencedByAnActiveTargetingRuleIsRejected() {
		Segment segment = createSegment.execute("acme", "beta", "Beta",
				List.of(new Condition(ConditionType.ATTRIBUTE, "plan", ConditionOperator.EQUALS, List.of("gold"))),
				"actor");
		Condition segmentCondition = new Condition(ConditionType.SEGMENT_MATCH, null, ConditionOperator.EQUALS,
				List.of(segment.getId()));
		addTargetingRule.execute("acme", "checkout", "production", 1, List.of(segmentCondition), trueVariantId,
				"actor");

		assertThatThrownBy(() -> retireSegment.execute("acme", "beta", "actor")).isInstanceOfSatisfying(
				ApiException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(409));
	}
}
