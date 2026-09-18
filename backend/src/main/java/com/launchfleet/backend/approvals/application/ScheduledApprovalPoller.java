package com.launchfleet.backend.approvals.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The only production caller of ApplyDueScheduledApprovals.execute() - a thin
 * @Scheduled wrapper around it, kept separate specifically so tests can call the core
 * logic directly against a fixed Clock instead of waiting on this real timer (locked
 * architecture rule 25). No human action is required for a scheduled request to
 * apply (rule 12) - this poll loop is what makes that automatic.
 */
@Component
class ScheduledApprovalPoller {

	private final ApplyDueScheduledApprovals applyDueScheduledApprovals;

	ScheduledApprovalPoller(ApplyDueScheduledApprovals applyDueScheduledApprovals) {
		this.applyDueScheduledApprovals = applyDueScheduledApprovals;
	}

	// A conservative interval: this is the production polling cadence, not something
	// tests wait on (they call ApplyDueScheduledApprovals.execute() directly against a
	// fixed Clock - see its Javadoc), but a long-enough initialDelay/fixedDelay also
	// keeps this poller from firing mid-test-suite and racing a test's own direct call.
	@Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
	void pollDueScheduledApprovals() {
		applyDueScheduledApprovals.execute();
	}
}
