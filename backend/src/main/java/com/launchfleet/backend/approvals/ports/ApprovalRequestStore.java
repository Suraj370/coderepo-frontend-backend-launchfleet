package com.launchfleet.backend.approvals.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.launchfleet.backend.approvals.domain.ApprovalRequest;

/**
 * The persistence boundary for ApprovalRequest - implemented by an infrastructure
 * adapter (see adapters.mongodb.MongoApprovalRequestStore). The application layer
 * depends only on this interface, matching FeatureFlagConfigStore's own pattern.
 */
public interface ApprovalRequestStore {

	Optional<ApprovalRequest> findById(String id);

	List<ApprovalRequest> findByProjectId(String projectId);

	/** The "only one PENDING request per flag+environment" invariant's read side (see save's Javadoc for the write side). */
	Optional<ApprovalRequest> findPendingByFeatureFlagIdAndEnvironmentId(String featureFlagId, String environmentId);

	/** Used by flag retirement to cascade-cancel every request that could still be approved/applied. */
	List<ApprovalRequest> findPendingOrScheduledByFeatureFlagId(String featureFlagId);

	/** Used by environment retirement to cascade-cancel every request that could still be approved/applied. */
	List<ApprovalRequest> findPendingOrScheduledByEnvironmentId(String environmentId);

	/** Used by the scheduled-application poller: every SCHEDULED request whose scheduledAt has arrived. */
	List<ApprovalRequest> findScheduledAtOrBefore(Instant now);

	/**
	 * Persists a request. Implementations must enforce the one-PENDING-per-scope
	 * invariant as a backstop (see the Mongo adapter's partial unique index) in
	 * addition to the application-level check already performed before this is
	 * called - never rely on the application check alone, since a race between two
	 * concurrent submissions can slip past it (locked architecture rule 6).
	 */
	ApprovalRequest save(ApprovalRequest request);

	void deleteAll();

}
