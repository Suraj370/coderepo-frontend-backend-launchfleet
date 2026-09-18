package com.launchfleet.backend.featureflags.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.launchfleet.backend.featureflags.application.SetRollout.AllocationInput;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.FlagType;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Application use case tests for setting/replacing/removing a rollout - fake,
 * in-memory ports, no Spring context, no MongoDB.
 */
class RolloutUseCasesTest {

	private final InMemoryFeatureFlagStore featureFlagStore = new InMemoryFeatureFlagStore();

	private final InMemoryFeatureFlagConfigStore featureFlagConfigStore = new InMemoryFeatureFlagConfigStore();

	private final InMemoryProjectLookup projectLookup = new InMemoryProjectLookup();

	private final InMemoryEnvironmentLookup environmentLookup = new InMemoryEnvironmentLookup();

	private final InMemorySegmentStore segmentStore = new InMemorySegmentStore();

	private FeatureFlagLookup flagLookup;

	private CreateFeatureFlag createFeatureFlag;

	private SetRollout setRollout;

	private RemoveRollout removeRollout;

	private ProjectRef project;

	private String trueVariantId;

	private String falseVariantId;

	@BeforeEach
	void setUp() {
		flagLookup = new FeatureFlagLookup(featureFlagStore, featureFlagConfigStore, projectLookup, environmentLookup);
		TargetingRuleValidator validator = new TargetingRuleValidator(segmentStore);

		createFeatureFlag = new CreateFeatureFlag(featureFlagStore, featureFlagConfigStore, flagLookup, new InMemoryActivityRecorder());
		setRollout = new SetRollout(featureFlagConfigStore, flagLookup, validator);
		removeRollout = new RemoveRollout(featureFlagConfigStore, flagLookup);

		project = projectLookup.addProject("acme");
		environmentLookup.addEnvironment(project.id(), "production");
		environmentLookup.addEnvironment(project.id(), "staging");

		FeatureFlagView flag = createFeatureFlag.execute("acme", "checkout", "Checkout", null, FlagType.BOOLEAN, null,
				"actor");
		falseVariantId = flag.flag().getVariants().get(0).id();
		trueVariantId = flag.flag().getVariants().get(1).id();
	}

	private List<AllocationInput> splitBetweenBothVariants() {
		return List.of(new AllocationInput(falseVariantId, 4000), new AllocationInput(trueVariantId, 6000));
	}

	private FeatureFlagConfig configFor(FeatureFlagView view, String environmentKey) {
		String environmentId = environmentLookup.findByProjectIdAndKey(project.id(), environmentKey).orElseThrow()
				.id();

		return view.configs().stream().filter(config -> config.getEnvironmentId().equals(environmentId)).findFirst()
				.orElseThrow();
	}

	@Test
	void settingARolloutStoresItAndBumpsVersion() {
		FeatureFlagView view = setRollout.execute("acme", "checkout", "production", splitBetweenBothVariants(),
				"actor");

		FeatureFlagConfig config = configFor(view, "production");
		assertThat(config.getRollout().allocations()).hasSize(2);
		assertThat(config.getVersion()).isEqualTo(2);
	}

	@Test
	void replacingARolloutOverwritesTheAllocationListAndBumpsVersionOnce() {
		setRollout.execute("acme", "checkout", "production", splitBetweenBothVariants(), "actor");

		FeatureFlagView replaced = setRollout.execute("acme", "checkout", "production",
				List.of(new AllocationInput(trueVariantId, 10000)), "editor");

		FeatureFlagConfig config = configFor(replaced, "production");
		assertThat(config.getRollout().allocations()).hasSize(1);
		assertThat(config.getVersion()).isEqualTo(3);
		assertThat(config.getUpdatedBy()).isEqualTo("editor");
	}

	@Test
	void removingARolloutClearsItAndBumpsVersion() {
		setRollout.execute("acme", "checkout", "production", splitBetweenBothVariants(), "actor");

		FeatureFlagView afterRemove = removeRollout.execute("acme", "checkout", "production", "editor");

		FeatureFlagConfig config = configFor(afterRemove, "production");
		assertThat(config.getRollout()).isNull();
		assertThat(config.getVersion()).isEqualTo(3);
	}

	@Test
	void settingARolloutWithAnUnknownVariantIsRejected() {
		List<AllocationInput> allocations = List.of(new AllocationInput("not-a-real-variant", 10000));

		assertThatThrownBy(() -> setRollout.execute("acme", "checkout", "production", allocations, "actor"))
				.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(400));
	}

	@Test
	void settingARolloutThatDoesNotSumToTenThousandIsRejected() {
		List<AllocationInput> allocations = List.of(new AllocationInput(falseVariantId, 4000),
				new AllocationInput(trueVariantId, 4000));

		assertThatThrownBy(() -> setRollout.execute("acme", "checkout", "production", allocations, "actor"))
				.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(400));
	}

	@Test
	void settingARolloutWithADuplicateVariantIsRejected() {
		List<AllocationInput> allocations = List.of(new AllocationInput(falseVariantId, 5000),
				new AllocationInput(falseVariantId, 5000));

		assertThatThrownBy(() -> setRollout.execute("acme", "checkout", "production", allocations, "actor"))
				.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(400));
	}

	@Test
	void rolloutsAreIsolatedPerEnvironment() {
		setRollout.execute("acme", "checkout", "production", splitBetweenBothVariants(), "actor");

		FeatureFlagView view = flagLookup.viewOf(featureFlagStore.findByProjectIdAndKey(project.id(), "checkout")
				.orElseThrow(), project);
		var stagingConfig = view.configs().stream()
				.filter(config -> config.getEnvironmentId()
						.equals(environmentLookup.findByProjectIdAndKey(project.id(), "staging").orElseThrow().id()))
				.findFirst().orElseThrow();

		assertThat(stagingConfig.getRollout()).isNull();
	}

	@Test
	void rolloutsAreIsolatedPerProject() {
		ProjectRef otherProject = projectLookup.addProject("globex");
		environmentLookup.addEnvironment(otherProject.id(), "production");
		createFeatureFlag.execute("globex", "checkout", "Checkout", null, FlagType.BOOLEAN, null, "actor");

		setRollout.execute("acme", "checkout", "production", splitBetweenBothVariants(), "actor");

		FeatureFlagView otherView = flagLookup.viewOf(
				featureFlagStore.findByProjectIdAndKey(otherProject.id(), "checkout").orElseThrow(), otherProject);
		assertThat(otherView.configs().get(0).getRollout()).isNull();
	}
}
