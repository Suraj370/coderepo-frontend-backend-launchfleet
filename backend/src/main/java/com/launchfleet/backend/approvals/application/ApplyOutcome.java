package com.launchfleet.backend.approvals.application;

/**
 * The result of attempting to apply an approval request's proposed configuration -
 * shared between the immediate-approve flow and the scheduled-application poller
 * (see ApplyProposedConfiguration), since both must react to the same set of outcomes
 * (locked architecture rules 9-13).
 */
sealed interface ApplyOutcome {

	record Applied(int newVersion) implements ApplyOutcome {
	}

	record Stale() implements ApplyOutcome {
	}

	record FlagRetired() implements ApplyOutcome {
	}

	record EnvironmentRetired() implements ApplyOutcome {
	}

	record Failed(String reason) implements ApplyOutcome {
	}

}
