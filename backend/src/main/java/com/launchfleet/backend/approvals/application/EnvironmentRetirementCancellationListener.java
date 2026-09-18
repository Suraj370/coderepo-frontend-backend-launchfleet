package com.launchfleet.backend.approvals.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.domain.CancellationReason;
import com.launchfleet.backend.approvals.ports.ApprovalRequestConflictException;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.launchfleet.backend.environments.ports.EnvironmentRetiredListener;

/** Mirrors FlagRetirementCancellationListener exactly, including its per-request conflict handling - see its Javadoc. */
@Component
class EnvironmentRetirementCancellationListener implements EnvironmentRetiredListener {

	private static final Logger log = LoggerFactory.getLogger(EnvironmentRetirementCancellationListener.class);

	private static final String SYSTEM_ACTOR = "system";

	private final ApprovalRequestStore approvalRequestStore;

	EnvironmentRetirementCancellationListener(ApprovalRequestStore approvalRequestStore) {
		this.approvalRequestStore = approvalRequestStore;
	}

	@Override
	public void onEnvironmentRetired(String projectId, String environmentId, String environmentKey,
			String actingUserId) {
		for (ApprovalRequest request : approvalRequestStore.findPendingOrScheduledByEnvironmentId(environmentId)) {
			try {
				request.cancel(SYSTEM_ACTOR, CancellationReason.ENVIRONMENT_RETIRED);
				approvalRequestStore.save(request);
			} catch (ApprovalRequestConflictException conflict) {
				log.debug("Approval request {} was already resolved by a concurrent operation; "
						+ "environment-retirement cancellation skipped for it.", request.getId());
			}
		}
	}
}
