package com.launchfleet.backend.experiments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.domain.Rollout;

/** Pure unit tests of Experiment's centralized transition logic - no Spring, no MongoDB. */
class ExperimentTest {

	private static final Rollout ALLOCATION = new Rollout(
			List.of(new Allocation("v-a", 5000), new Allocation("v-b", 5000)));

	private Experiment freshDraft() {
		return Experiment.create("project-1", "env-1", "flag-1", "checkout-copy-test", "Checkout Copy Test", "desc",
				"alice");
	}

	@Test
	void createStartsInDraftWithVersionOne() {
		Experiment experiment = freshDraft();

		assertThat(experiment.getStatus()).isEqualTo(ExperimentStatus.DRAFT);
		assertThat(experiment.getVersion()).isEqualTo(1);
		assertThat(experiment.getAllocation()).isNull();
		assertThat(experiment.getConversionEventName()).isNull();
	}

	@Test
	void cannotStartWithoutAnAllocation() {
		Experiment experiment = freshDraft();
		experiment.updateConfiguration("Checkout Copy Test", "desc", null, "purchase_completed", "alice");

		assertThatThrownBy(() -> experiment.start("alice")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cannotStartWithoutAConversionEventName() {
		Experiment experiment = freshDraft();
		experiment.updateConfiguration("Checkout Copy Test", "desc", ALLOCATION, null, "alice");

		assertThatThrownBy(() -> experiment.start("alice")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void startSucceedsWithAValidConfigurationAndBumpsVersion() {
		Experiment experiment = freshDraft();
		experiment.updateConfiguration("Checkout Copy Test", "desc", ALLOCATION, "purchase_completed", "alice");
		int versionBeforeStart = experiment.getVersion();

		experiment.start("alice");

		assertThat(experiment.getStatus()).isEqualTo(ExperimentStatus.RUNNING);
		assertThat(experiment.getVersion()).isEqualTo(versionBeforeStart + 1);
	}

	@Test
	void configurationCannotBeChangedOnceRunning() {
		Experiment experiment = freshDraft();
		experiment.updateConfiguration("Checkout Copy Test", "desc", ALLOCATION, "purchase_completed", "alice");
		experiment.start("alice");

		assertThatThrownBy(() -> experiment.updateConfiguration("New name", "desc", ALLOCATION, "purchase_completed",
				"alice")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void allocationFrozenAtStartIsNotAffectedByLaterCallsToUpdateConfigurationBecauseTheyAreRejected() {
		Experiment experiment = freshDraft();
		experiment.updateConfiguration("Checkout Copy Test", "desc", ALLOCATION, "purchase_completed", "alice");
		experiment.start("alice");
		Rollout frozenAllocation = experiment.getAllocation();

		Rollout differentAllocation = new Rollout(List.of(new Allocation("v-a", 2000), new Allocation("v-b", 8000)));
		assertThatThrownBy(() -> experiment.updateConfiguration("Checkout Copy Test", "desc", differentAllocation,
				"purchase_completed", "alice")).isInstanceOf(IllegalStateException.class);
		assertThat(experiment.getAllocation()).isSameAs(frozenAllocation);
	}

	@Test
	void completeRequiresRunning() {
		Experiment draft = freshDraft();
		assertThatThrownBy(() -> draft.complete("alice")).isInstanceOf(IllegalStateException.class);

		Experiment running = freshDraft();
		running.updateConfiguration("Checkout Copy Test", "desc", ALLOCATION, "purchase_completed", "alice");
		running.start("alice");
		running.complete("alice");

		assertThat(running.getStatus()).isEqualTo(ExperimentStatus.COMPLETED);
	}

	@Test
	void completedIsTerminal() {
		Experiment experiment = freshDraft();
		experiment.updateConfiguration("Checkout Copy Test", "desc", ALLOCATION, "purchase_completed", "alice");
		experiment.start("alice");
		experiment.complete("alice");

		assertThatThrownBy(() -> experiment.complete("alice")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> experiment.cancel("system")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> experiment.start("alice")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cancelWorksFromDraftAndRunningButNotFromTerminalStatuses() {
		Experiment draft = freshDraft();
		draft.cancel("system");
		assertThat(draft.getStatus()).isEqualTo(ExperimentStatus.CANCELLED);

		Experiment running = freshDraft();
		running.updateConfiguration("Checkout Copy Test", "desc", ALLOCATION, "purchase_completed", "alice");
		running.start("alice");
		running.cancel("system");
		assertThat(running.getStatus()).isEqualTo(ExperimentStatus.CANCELLED);

		assertThatThrownBy(() -> running.cancel("system")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void invalidTransitionsAreImpossible() {
		Experiment draft = freshDraft();
		assertThatThrownBy(() -> draft.complete("alice")).isInstanceOf(IllegalStateException.class);

		Experiment cancelled = freshDraft();
		cancelled.cancel("system");
		assertThatThrownBy(() -> cancelled.start("alice")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> cancelled.complete("alice")).isInstanceOf(IllegalStateException.class);
	}
}
