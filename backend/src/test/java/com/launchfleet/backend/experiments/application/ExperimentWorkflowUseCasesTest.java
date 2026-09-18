package com.launchfleet.backend.experiments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.launchfleet.backend.experiments.application.UpdateExperiment.AllocationInput;
import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.experiments.domain.ExperimentAssignment;
import com.launchfleet.backend.experiments.domain.ExperimentEvent;
import com.launchfleet.backend.experiments.domain.ExperimentStatus;
import com.launchfleet.backend.featureflags.application.TargetingRuleValidator;
import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.ConditionOperator;
import com.launchfleet.backend.featureflags.domain.ConditionType;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.FlagType;
import com.launchfleet.backend.featureflags.domain.TargetingRule;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Application-level coverage for the Phase 7 experimentation workflow, using fake
 * in-memory ports - no Spring, no MongoDB (see ApprovalWorkflowUseCasesTest for the
 * same pattern in approvals/). RBAC and real Mongo uniqueness/concurrency are
 * covered separately by ExperimentApiTest and ExperimentAssignmentConcurrencyTest.
 */
class ExperimentWorkflowUseCasesTest {

	private final InMemoryProjectLookup projectLookup = new InMemoryProjectLookup();

	private final InMemoryEnvironmentLookup environmentLookup = new InMemoryEnvironmentLookup();

	private final InMemoryFeatureFlagStore featureFlagStore = new InMemoryFeatureFlagStore();

	private final InMemoryFeatureFlagConfigStore featureFlagConfigStore = new InMemoryFeatureFlagConfigStore();

	private final InMemoryExperimentStore experimentStore = new InMemoryExperimentStore();

	private final InMemoryExperimentAssignmentStore assignmentStore = new InMemoryExperimentAssignmentStore();

	private final InMemoryExperimentEventStore eventStore = new InMemoryExperimentEventStore();

	private final InMemorySegmentStore segmentStore = new InMemorySegmentStore();

	private CreateExperiment createExperiment;

	private UpdateExperiment updateExperiment;

	private StartExperiment startExperiment;

	private CompleteExperiment completeExperiment;

	private CreateExperimentAssignment createExperimentAssignment;

	private RecordExperimentEvent recordExperimentEvent;

	private GetExperimentMetrics getExperimentMetrics;

	private ExperimentFlagRetirementListener flagRetirementListener;

	private ExperimentEnvironmentRetirementListener environmentRetirementListener;

	private ProjectRef project;

	private EnvironmentRef environment;

	private FeatureFlag flag;

	private String variantA;

	private String variantB;

	@BeforeEach
	void setUp() {
		ExperimentLookup lookup = new ExperimentLookup(projectLookup, environmentLookup, featureFlagStore,
				experimentStore);
		TargetingRuleValidator validator = new TargetingRuleValidator(segmentStore);
		FlagTargetingEligibility eligibility = new FlagTargetingEligibility(segmentStore);

		createExperiment = new CreateExperiment(lookup, experimentStore, new InMemoryActivityRecorder());
		updateExperiment = new UpdateExperiment(lookup, experimentStore, validator);
		startExperiment = new StartExperiment(lookup, experimentStore, new InMemoryActivityRecorder());
		completeExperiment = new CompleteExperiment(lookup, experimentStore, new InMemoryActivityRecorder());
		createExperimentAssignment = new CreateExperimentAssignment(lookup, featureFlagConfigStore, assignmentStore,
				eligibility);
		recordExperimentEvent = new RecordExperimentEvent(lookup, assignmentStore, eventStore);
		getExperimentMetrics = new GetExperimentMetrics(lookup, assignmentStore, eventStore);
		flagRetirementListener = new ExperimentFlagRetirementListener(experimentStore);
		environmentRetirementListener = new ExperimentEnvironmentRetirementListener(experimentStore);

		project = projectLookup.addProject("acme");
		environment = environmentLookup.addEnvironment(project.id(), "production");
		flag = featureFlagStore.save(FeatureFlag.create(project.id(), "checkout", "Checkout", null,
				FlagType.MULTIVARIANT, List.of(
						new com.launchfleet.backend.featureflags.domain.Variant(null, "a", "A", "a", 0),
						new com.launchfleet.backend.featureflags.domain.Variant(null, "b", "B", "b", 1)),
				"creator"));
		variantA = flag.getVariants().get(0).id();
		variantB = flag.getVariants().get(1).id();
		featureFlagConfigStore.save(
				FeatureFlagConfig.createDisabled(flag.getId(), environment.id(), project.id(), variantA, "creator"));
		// Enable the flag - CreateExperimentAssignment requires it to be enabled.
		FeatureFlagConfig config = featureFlagConfigStore
				.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id()).orElseThrow();
		config.updateEnabled(true, "creator");
		featureFlagConfigStore.save(config);
	}

	private Experiment createDraft(String key) {
		return createExperiment.execute("acme", "production", "checkout", key, "Checkout Experiment", "desc",
				"alice");
	}

	private Experiment configureAndStart(String key) {
		Experiment experiment = createDraft(key);
		experiment = updateExperiment.execute("acme", key, "Checkout Experiment", "desc",
				List.of(new AllocationInput(variantA, 5000), new AllocationInput(variantB, 5000)),
				"purchase_completed", "alice");
		return startExperiment.execute("acme", key, "alice");
	}

	// --- CREATION / VALIDATION ---

	@Test
	void validExperimentCreationStartsInDraft() {
		Experiment experiment = createDraft("exp-1");

		assertThat(experiment.getStatus()).isEqualTo(ExperimentStatus.DRAFT);
		assertThat(experiment.getFeatureFlagId()).isEqualTo(flag.getId());
		assertThat(experiment.getEnvironmentId()).isEqualTo(environment.id());
	}

	@Test
	void invalidAllocationNotSummingToTenThousandIsRejected() {
		createDraft("exp-2");

		assertThatThrownBy(() -> updateExperiment.execute("acme", "exp-2", "Checkout Experiment", "desc",
				List.of(new AllocationInput(variantA, 4000), new AllocationInput(variantB, 5000)),
				"purchase_completed", "alice")).isInstanceOf(ApiException.class);
	}

	@Test
	void duplicateVariantIdsInAllocationAreRejected() {
		createDraft("exp-3");

		assertThatThrownBy(() -> updateExperiment.execute("acme", "exp-3", "Checkout Experiment", "desc",
				List.of(new AllocationInput(variantA, 5000), new AllocationInput(variantA, 5000)),
				"purchase_completed", "alice")).isInstanceOf(ApiException.class);
	}

	@Test
	void unknownVariantIdInAllocationIsRejected() {
		createDraft("exp-4");

		assertThatThrownBy(() -> updateExperiment.execute("acme", "exp-4", "Checkout Experiment", "desc",
				List.of(new AllocationInput("not-a-real-variant", 10000)), "purchase_completed", "alice"))
				.isInstanceOf(ApiException.class);
	}

	// --- LIFECYCLE ---

	@Test
	void draftToRunningToCompletedSucceeds() {
		Experiment running = configureAndStart("exp-5");
		assertThat(running.getStatus()).isEqualTo(ExperimentStatus.RUNNING);

		Experiment completed = completeExperiment.execute("acme", "exp-5", "alice");
		assertThat(completed.getStatus()).isEqualTo(ExperimentStatus.COMPLETED);
	}

	@Test
	void invalidLifecycleTransitionIsRejected() {
		createDraft("exp-6");

		assertThatThrownBy(() -> completeExperiment.execute("acme", "exp-6", "alice"))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void startWithoutAllocationOrConversionEventIsRejected() {
		createDraft("exp-7");

		assertThatThrownBy(() -> startExperiment.execute("acme", "exp-7", "alice")).isInstanceOf(ApiException.class);
	}

	@Test
	void flagRetirementCancelsActiveExperiments() {
		Experiment draft = createDraft("exp-8");
		Experiment running = configureAndStart("exp-9");

		flag.retire("actor");
		featureFlagStore.save(flag);
		flagRetirementListener.onFeatureFlagRetired(project.id(), flag.getId(), "checkout", "actor");

		assertThat(experimentStore.findById(draft.getId()).orElseThrow().getStatus())
				.isEqualTo(ExperimentStatus.CANCELLED);
		assertThat(experimentStore.findById(running.getId()).orElseThrow().getStatus())
				.isEqualTo(ExperimentStatus.CANCELLED);
	}

	@Test
	void environmentRetirementCancelsActiveExperiments() {
		Experiment running = configureAndStart("exp-10");

		environmentRetirementListener.onEnvironmentRetired(project.id(), environment.id(), "production", "actor");

		assertThat(experimentStore.findById(running.getId()).orElseThrow().getStatus())
				.isEqualTo(ExperimentStatus.CANCELLED);
	}

	// --- ASSIGNMENT ---

	@Test
	void assignmentIsCreatedForARunningExperiment() {
		configureAndStart("exp-11");

		ExperimentAssignment assignment = createExperimentAssignment.execute("acme", "production", "exp-11", "alice");

		assertThat(assignment.getVariantId()).isIn(variantA, variantB);
	}

	@Test
	void assignmentIsStickyForTheSameUser() {
		configureAndStart("exp-12");

		ExperimentAssignment first = createExperimentAssignment.execute("acme", "production", "exp-12", "bob");
		ExperimentAssignment second = createExperimentAssignment.execute("acme", "production", "exp-12", "bob");

		assertThat(second.getVariantId()).isEqualTo(first.getVariantId());
		assertThat(second.getId()).isEqualTo(first.getId());
	}

	@Test
	void assignmentIsRejectedForANonRunningExperiment() {
		createDraft("exp-13");

		assertThatThrownBy(() -> createExperimentAssignment.execute("acme", "production", "exp-13", "alice"))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void assignmentIsRejectedWhenTheFlagIsDisabledInThisEnvironment() {
		configureAndStart("exp-14");
		FeatureFlagConfig config = featureFlagConfigStore
				.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id()).orElseThrow();
		config.updateEnabled(false, "actor");
		featureFlagConfigStore.save(config);

		assertThatThrownBy(() -> createExperimentAssignment.execute("acme", "production", "exp-14", "alice"))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void assignmentIsRejectedWhenTheUserDoesNotMatchTargetingRules() {
		configureAndStart("exp-tr-1");
		addUserKeyOnlyTargetingRule("alice");

		assertThatThrownBy(() -> createExperimentAssignment.execute("acme", "production", "exp-tr-1", "mallory"))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void assignmentIsCreatedUsingTheExperimentsFrozenAllocationWhenTheUserMatchesTargetingRules() {
		configureAndStart("exp-tr-2");
		addUserKeyOnlyTargetingRule("alice");

		ExperimentAssignment assignment = createExperimentAssignment.execute("acme", "production", "exp-tr-2",
				"alice");

		assertThat(assignment.getVariantId()).isIn(variantA, variantB);
	}

	@Test
	void assignmentEligibilityAlsoRespectsSegmentBasedTargetingRules() {
		configureAndStart("exp-tr-2b");
		com.launchfleet.backend.featureflags.domain.Segment segment = segmentStore
				.save(com.launchfleet.backend.featureflags.domain.Segment.create(project.id(), "beta-users", "Beta Users",
						List.of(new Condition(ConditionType.USER_KEY, null, ConditionOperator.IN, List.of("ivan"))),
						"actor"));
		FeatureFlagConfig config = featureFlagConfigStore
				.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id()).orElseThrow();
		Condition segmentCondition = new Condition(ConditionType.SEGMENT_MATCH, null, ConditionOperator.EQUALS,
				List.of(segment.getId()));
		config.updateTargetingRules(List.of(TargetingRule.create(1, List.of(segmentCondition), variantA)), "actor");
		featureFlagConfigStore.save(config);

		assertThatThrownBy(() -> createExperimentAssignment.execute("acme", "production", "exp-tr-2b", "not-ivan"))
				.isInstanceOf(ApiException.class);

		ExperimentAssignment assignment = createExperimentAssignment.execute("acme", "production", "exp-tr-2b", "ivan");
		assertThat(assignment.getVariantId()).isIn(variantA, variantB);
	}

	@Test
	void existingAssignmentSurvivesAFlagRolloutChange() {
		configureAndStart("exp-tr-3");
		ExperimentAssignment before = createExperimentAssignment.execute("acme", "production", "exp-tr-3", "erin");

		FeatureFlagConfig config = featureFlagConfigStore
				.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id()).orElseThrow();
		config.updateRollout(new com.launchfleet.backend.featureflags.domain.Rollout(
				List.of(new com.launchfleet.backend.featureflags.domain.Allocation(variantA, 1000),
						new com.launchfleet.backend.featureflags.domain.Allocation(variantB, 9000))),
				"actor");
		featureFlagConfigStore.save(config);

		ExperimentAssignment after = createExperimentAssignment.execute("acme", "production", "exp-tr-3", "erin");
		assertThat(after.getVariantId()).isEqualTo(before.getVariantId());
		assertThat(after.getId()).isEqualTo(before.getId());
	}

	@Test
	void existingAssignmentSurvivesATargetingRuleChangeThatWouldNowExcludeTheUser() {
		configureAndStart("exp-tr-4");
		ExperimentAssignment before = createExperimentAssignment.execute("acme", "production", "exp-tr-4", "frank");

		// A targeting rule added AFTER the assignment that would exclude "frank" going forward.
		addUserKeyOnlyTargetingRule("someone-else");

		ExperimentAssignment after = createExperimentAssignment.execute("acme", "production", "exp-tr-4", "frank");
		assertThat(after.getVariantId()).isEqualTo(before.getVariantId());
		assertThat(after.getId()).isEqualTo(before.getId());
	}

	@Test
	void existingAssignmentSurvivesTheFlagBeingDisabled() {
		configureAndStart("exp-tr-5");
		ExperimentAssignment before = createExperimentAssignment.execute("acme", "production", "exp-tr-5", "grace");

		FeatureFlagConfig config = featureFlagConfigStore
				.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id()).orElseThrow();
		config.updateEnabled(false, "actor");
		featureFlagConfigStore.save(config);

		ExperimentAssignment after = createExperimentAssignment.execute("acme", "production", "exp-tr-5", "grace");
		assertThat(after.getVariantId()).isEqualTo(before.getVariantId());
		assertThat(after.getId()).isEqualTo(before.getId());
	}

	@Test
	void newEligibleUserIsAssignedUsingTheExperimentsAllocationIndependentlyOfTheFlagsRollout() {
		Experiment running = configureAndStart("exp-tr-6");

		// The flag's own live rollout heavily favors variantA - the experiment's frozen
		// 50/50 allocation (set up in configureAndStart) must be what actually governs.
		FeatureFlagConfig config = featureFlagConfigStore
				.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id()).orElseThrow();
		config.updateRollout(new com.launchfleet.backend.featureflags.domain.Rollout(
				List.of(new com.launchfleet.backend.featureflags.domain.Allocation(variantA, 10000),
						new com.launchfleet.backend.featureflags.domain.Allocation(variantB, 0))),
				"actor");
		featureFlagConfigStore.save(config);

		ExperimentAssignment assignment = createExperimentAssignment.execute("acme", "production", "exp-tr-6",
				"heidi");

		assertThat(assignment.getVariantId()).isIn(running.getAllocation().allocations().stream()
				.map(com.launchfleet.backend.featureflags.domain.Allocation::variantId).toList());
	}

	/** A USER_KEY-only targeting rule matching exactly one userKey - the rule's own target variant is irrelevant to eligibility, only whether it matches. */
	private void addUserKeyOnlyTargetingRule(String matchingUserKey) {
		FeatureFlagConfig config = featureFlagConfigStore
				.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id()).orElseThrow();
		Condition condition = new Condition(ConditionType.USER_KEY, null, ConditionOperator.EQUALS,
				List.of(matchingUserKey));
		TargetingRule rule = TargetingRule.create(1, List.of(condition), variantA);
		config.updateTargetingRules(List.of(rule), "actor");
		featureFlagConfigStore.save(config);
	}

	@Test
	void normalFlagRolloutChangesDoNotAffectTheExperimentsFrozenAllocation() {
		Experiment running = configureAndStart("exp-15");
		var allocationBefore = running.getAllocation();

		// A completely unrelated, direct edit to the flag's OWN live rollout.
		FeatureFlagConfig config = featureFlagConfigStore
				.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id()).orElseThrow();
		config.updateRollout(new com.launchfleet.backend.featureflags.domain.Rollout(
				List.of(new com.launchfleet.backend.featureflags.domain.Allocation(variantA, 1000),
						new com.launchfleet.backend.featureflags.domain.Allocation(variantB, 9000))),
				"actor");
		featureFlagConfigStore.save(config);

		Experiment reloaded = experimentStore.findById(running.getId()).orElseThrow();
		assertThat(reloaded.getAllocation().allocations()).containsExactlyInAnyOrderElementsOf(
				allocationBefore.allocations());
	}

	// --- EVENTS ---

	@Test
	void eventWithoutAnExistingAssignmentIsRejected() {
		configureAndStart("exp-16");

		assertThatThrownBy(() -> recordExperimentEvent.execute("acme", "production", "exp-16", "nobody",
				"purchase_completed")).isInstanceOf(ApiException.class);
	}

	@Test
	void eventAssociatesWithThePersistedAssignmentsVariant() {
		configureAndStart("exp-17");
		ExperimentAssignment assignment = createExperimentAssignment.execute("acme", "production", "exp-17", "carol");

		ExperimentEvent event = recordExperimentEvent.execute("acme", "production", "exp-17", "carol",
				"purchase_completed");

		assertThat(event.getVariantId()).isEqualTo(assignment.getVariantId());
	}

	@Test
	void eventIsRejectedOnceTheExperimentIsNoLongerRunning() {
		Experiment running = configureAndStart("exp-18");
		createExperimentAssignment.execute("acme", "production", "exp-18", "dave");
		completeExperiment.execute("acme", "exp-18", "alice");

		assertThatThrownBy(() -> recordExperimentEvent.execute("acme", "production", "exp-18", "dave",
				"purchase_completed")).isInstanceOf(ApiException.class);
	}

	// --- METRICS ---

	@Test
	void metricsReflectAssignmentsAndConversionsPerVariant() {
		configureAndStart("exp-19");
		// Force deterministic, known variants for three users by asserting against
		// whatever RolloutAssigner actually returns for each - the goal is exercising
		// count/rate math, not re-testing RolloutAssigner's own hashing (already
		// covered by RolloutAssignerTest/RolloutHasherCompatibilityTest).
		ExperimentAssignment a1 = createExperimentAssignment.execute("acme", "production", "exp-19", "user-1");
		ExperimentAssignment a2 = createExperimentAssignment.execute("acme", "production", "exp-19", "user-2");
		createExperimentAssignment.execute("acme", "production", "exp-19", "user-3");
		recordExperimentEvent.execute("acme", "production", "exp-19", "user-1", "purchase_completed");

		List<ExperimentVariantMetrics> metrics = getExperimentMetrics.execute("acme", "exp-19");

		long totalAssigned = metrics.stream().mapToLong(ExperimentVariantMetrics::assignedCount).sum();
		assertThat(totalAssigned).isEqualTo(3);

		ExperimentVariantMetrics variantOfUser1 = metrics.stream().filter(m -> m.variantId().equals(a1.getVariantId()))
				.findFirst().orElseThrow();
		assertThat(variantOfUser1.conversionCount()).isGreaterThanOrEqualTo(1);
		assertThat(variantOfUser1.conversionRate()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
	}

	@Test
	void metricsHandleZeroAssignmentsAndZeroConversionsWithoutDivisionByZero() {
		createDraft("exp-20");
		updateExperiment.execute("acme", "exp-20", "Checkout Experiment", "desc",
				List.of(new AllocationInput(variantA, 5000), new AllocationInput(variantB, 5000)),
				"purchase_completed", "alice");
		startExperiment.execute("acme", "exp-20", "alice");

		List<ExperimentVariantMetrics> metrics = getExperimentMetrics.execute("acme", "exp-20");

		assertThat(metrics).hasSize(2);
		for (ExperimentVariantMetrics variantMetrics : metrics) {
			assertThat(variantMetrics.assignedCount()).isZero();
			assertThat(variantMetrics.conversionCount()).isZero();
			assertThat(variantMetrics.conversionRate()).isZero();
		}
	}

	@Test
	void metricsForADraftExperimentWithNoAllocationAreEmpty() {
		createDraft("exp-21");

		assertThat(getExperimentMetrics.execute("acme", "exp-21")).isEmpty();
	}
}
