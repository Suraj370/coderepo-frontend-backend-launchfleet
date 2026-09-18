package com.launchfleet.backend.featureflags.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.launchfleet.backend.featureflags.domain.FlagStatus;
import com.launchfleet.backend.featureflags.domain.FlagType;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Application use case tests using fake, in-memory port implementations - no Spring
 * context, no MongoDB. This is the structural payoff of the ports/adapters boundary:
 * project/environment resolution, uniqueness checks, and cross-aggregate coordination
 * (creating one config per environment) are verified without booting anything.
 */
class FeatureFlagUseCasesTest {

	private final InMemoryFeatureFlagStore featureFlagStore = new InMemoryFeatureFlagStore();

	private final InMemoryFeatureFlagConfigStore featureFlagConfigStore = new InMemoryFeatureFlagConfigStore();

	private final InMemoryProjectLookup projectLookup = new InMemoryProjectLookup();

	private final InMemoryEnvironmentLookup environmentLookup = new InMemoryEnvironmentLookup();

	private FeatureFlagLookup lookup;

	private CreateFeatureFlag createFeatureFlag;

	private GetFeatureFlag getFeatureFlag;

	private ListFeatureFlags listFeatureFlags;

	private UpdateFeatureFlag updateFeatureFlag;

	private UpdateEnvironmentConfig updateEnvironmentConfig;

	private RetireFeatureFlag retireFeatureFlag;

	private ProjectRef project;

	@BeforeEach
	void setUp() {
		lookup = new FeatureFlagLookup(featureFlagStore, featureFlagConfigStore, projectLookup, environmentLookup);
		createFeatureFlag = new CreateFeatureFlag(featureFlagStore, featureFlagConfigStore, lookup, new InMemoryActivityRecorder());
		getFeatureFlag = new GetFeatureFlag(lookup);
		listFeatureFlags = new ListFeatureFlags(featureFlagStore, featureFlagConfigStore, lookup);
		updateFeatureFlag = new UpdateFeatureFlag(featureFlagStore, lookup);
		updateEnvironmentConfig = new UpdateEnvironmentConfig(featureFlagConfigStore, lookup);
		retireFeatureFlag = new RetireFeatureFlag(featureFlagStore, lookup, java.util.List.of(), new InMemoryActivityRecorder());

		project = projectLookup.addProject("acme");
		environmentLookup.addEnvironment(project.id(), "production");
	}

	@Test
	void creatingAFlagAutoCreatesADisabledConfigPerExistingEnvironment() {
		FeatureFlagView view = createFeatureFlag.execute("acme", "new-flag", "New Flag", null, FlagType.BOOLEAN, null,
				"actor");

		assertThat(view.configs()).hasSize(1);
		assertThat(view.configs().get(0).isEnabled()).isFalse();
		assertThat(view.environments()).extracting(env -> env.key()).containsExactly("production");
	}

	@Test
	void duplicateFlagKeyWithinTheSameProjectIsRejected() {
		createFeatureFlag.execute("acme", "dup", "First", null, FlagType.BOOLEAN, null, "actor");

		assertThatThrownBy(
				() -> createFeatureFlag.execute("acme", "dup", "Second", null, FlagType.BOOLEAN, null, "actor"))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(409));
	}

	@Test
	void creatingAFlagInAnUnknownProjectFails() {
		assertThatThrownBy(() -> createFeatureFlag.execute("does-not-exist", "key", "Name", null, FlagType.BOOLEAN,
				null, "actor")).isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(404));
	}

	@Test
	void getReturnsNotFoundForAnUnknownFlagKey() {
		assertThatThrownBy(() -> getFeatureFlag.execute("acme", "nope")).isInstanceOfSatisfying(ApiException.class,
				exception -> assertThat(exception.getStatusCode()).isEqualTo(404));
	}

	@Test
	void listOnlyReturnsFlagsForTheRequestedProject() {
		projectLookup.addProject("globex");
		createFeatureFlag.execute("acme", "acme-flag", "Acme Flag", null, FlagType.BOOLEAN, null, "actor");
		createFeatureFlag.execute("globex", "globex-flag", "Globex Flag", null, FlagType.BOOLEAN, null, "actor");

		assertThat(listFeatureFlags.execute("acme")).hasSize(1);
		assertThat(listFeatureFlags.execute("acme").get(0).flag().getKey()).isEqualTo("acme-flag");
	}

	@Test
	void updateChangesNameAndDescription() {
		createFeatureFlag.execute("acme", "key", "Original", null, FlagType.BOOLEAN, null, "actor");

		FeatureFlagView view = updateFeatureFlag.execute("acme", "key", "Renamed", "new desc", "editor");

		assertThat(view.flag().getName()).isEqualTo("Renamed");
	}

	@Test
	void enablingAnUnknownEnvironmentIsRejected() {
		createFeatureFlag.execute("acme", "key", "Name", null, FlagType.BOOLEAN, null, "actor");

		assertThatThrownBy(
				() -> updateEnvironmentConfig.execute("acme", "key", "does-not-exist", true, null, "actor"))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(404));
	}

	@Test
	void enablingAKnownEnvironmentBumpsVersion() {
		createFeatureFlag.execute("acme", "key", "Name", null, FlagType.BOOLEAN, null, "actor");

		FeatureFlagView view = updateEnvironmentConfig.execute("acme", "key", "production", true, null, "actor");

		assertThat(view.configs().get(0).isEnabled()).isTrue();
		assertThat(view.configs().get(0).getVersion()).isEqualTo(2);
	}

	@Test
	void changingTheDefaultVariantRequiresItToBelongToTheFlag() {
		createFeatureFlag.execute("acme", "key", "Name", null, FlagType.BOOLEAN, null, "actor");

		assertThatThrownBy(() -> updateEnvironmentConfig.execute("acme", "key", "production", null,
				"not-a-real-variant-id", "actor")).isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(400));
	}

	@Test
	void changingTheDefaultVariantToAValidVariantSucceeds() {
		FeatureFlagView created = createFeatureFlag.execute("acme", "key", "Name", null, FlagType.BOOLEAN, null,
				"actor");
		String trueVariantId = created.flag().getVariants().get(1).id();

		FeatureFlagView view = updateEnvironmentConfig.execute("acme", "key", "production", null, trueVariantId,
				"actor");

		assertThat(view.configs().get(0).getDefaultVariantId()).isEqualTo(trueVariantId);
		assertThat(view.configs().get(0).getVersion()).isEqualTo(2);
	}

	@Test
	void retiringAFlagTransitionsItsStatus() {
		createFeatureFlag.execute("acme", "key", "Name", null, FlagType.BOOLEAN, null, "actor");

		FeatureFlagView view = retireFeatureFlag.execute("acme", "key", "actor");

		assertThat(view.flag().getStatus()).isEqualTo(FlagStatus.RETIRED);
	}

	@Test
	void retiringAnAlreadyRetiredFlagIsRejectedAsAConflict() {
		createFeatureFlag.execute("acme", "key", "Name", null, FlagType.BOOLEAN, null, "actor");
		retireFeatureFlag.execute("acme", "key", "actor");

		assertThatThrownBy(() -> retireFeatureFlag.execute("acme", "key", "actor")).isInstanceOfSatisfying(
				ApiException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(409));
	}
}
