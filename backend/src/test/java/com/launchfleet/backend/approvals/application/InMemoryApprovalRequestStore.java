package com.launchfleet.backend.approvals.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.domain.ApprovalStatus;
import com.launchfleet.backend.approvals.ports.ApprovalRequestConflictException;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.launchfleet.backend.shared.ApiException;

/**
 * A fake, in-memory ApprovalRequestStore - mirrors featureflags' InMemory*Store
 * fakes and enforces the same one-PENDING-per-scope backstop plus the same
 * persistenceVersion-conditioned lifecycle-concurrency check the Mongo adapter
 * enforces (see MongoApprovalRequestStore).
 *
 * Every "find" returns a fresh copy, never the stored reference - a caller mutates
 * the returned ApprovalRequest via its own domain methods (approve/reject/cancel/
 * etc.) before calling save(); without a defensive copy here, that in-place mutation
 * would alias and corrupt the "before" snapshot this fake still has stored, silently
 * defeating the version check below (the exact bug already found and fixed once for
 * InMemoryFeatureFlagConfigStore - see its Javadoc).
 */
class InMemoryApprovalRequestStore implements ApprovalRequestStore {

	private static final int CONFLICT = 409;

	private final Map<String, ApprovalRequest> byId = new ConcurrentHashMap<>();

	@Override
	public Optional<ApprovalRequest> findById(String id) {
		return Optional.ofNullable(byId.get(id)).map(InMemoryApprovalRequestStore::copyOf);
	}

	@Override
	public List<ApprovalRequest> findByProjectId(String projectId) {
		return byId.values().stream().filter(r -> r.getProjectId().equals(projectId))
				.map(InMemoryApprovalRequestStore::copyOf).toList();
	}

	@Override
	public Optional<ApprovalRequest> findPendingByFeatureFlagIdAndEnvironmentId(String featureFlagId,
			String environmentId) {
		return byId.values().stream()
				.filter(r -> r.getFeatureFlagId().equals(featureFlagId) && r.getEnvironmentId().equals(environmentId)
						&& r.getStatus() == ApprovalStatus.PENDING)
				.findFirst().map(InMemoryApprovalRequestStore::copyOf);
	}

	@Override
	public List<ApprovalRequest> findPendingOrScheduledByFeatureFlagId(String featureFlagId) {
		return byId.values().stream().filter(r -> r.getFeatureFlagId().equals(featureFlagId) && isActive(r))
				.map(InMemoryApprovalRequestStore::copyOf).toList();
	}

	@Override
	public List<ApprovalRequest> findPendingOrScheduledByEnvironmentId(String environmentId) {
		return byId.values().stream().filter(r -> r.getEnvironmentId().equals(environmentId) && isActive(r))
				.map(InMemoryApprovalRequestStore::copyOf).toList();
	}

	@Override
	public List<ApprovalRequest> findScheduledAtOrBefore(Instant now) {
		return byId.values().stream()
				.filter(r -> r.getStatus() == ApprovalStatus.SCHEDULED && !r.getScheduledAt().isAfter(now))
				.map(InMemoryApprovalRequestStore::copyOf).toList();
	}

	@Override
	public synchronized ApprovalRequest save(ApprovalRequest request) {
		if (request.getId() == null) {
			if (request.getStatus() == ApprovalStatus.PENDING
					&& findPendingByFeatureFlagIdAndEnvironmentId(request.getFeatureFlagId(), request.getEnvironmentId())
							.isPresent()) {
				throw new ApiException(CONFLICT, "PENDING_REQUEST_EXISTS",
						"A pending approval request already exists for this flag and environment.");
			}

			ApprovalRequest toStore = ApprovalRequest.reconstitute(UUID.randomUUID().toString(),
					request.getProjectId(), request.getFeatureFlagId(), request.getEnvironmentId(),
					request.getBaseConfigVersion(), request.getProposedConfig(), request.getStatus(),
					request.getSubmittedBy(), request.getSubmittedAt(), request.getReviewedBy(),
					request.getReviewedAt(), request.getApprovalComment(), request.getRejectionComment(),
					request.getScheduledAt(), request.getAppliedVersion(), request.getCancellationReason(),
					request.getCancelledBy(), request.getCancelledAt(), 0);
			byId.put(toStore.getId(), toStore);

			return copyOf(toStore);
		}

		// The lifecycle-concurrency guard: mirrors MongoApprovalRequestStore's real
		// findAndModify condition exactly, just in-memory - only a caller whose
		// observed persistenceVersion still matches the stored one may commit.
		ApprovalRequest current = byId.get(request.getId());
		if (current == null || current.getPersistenceVersion() != request.getPersistenceVersion()) {
			throw new ApprovalRequestConflictException(
					"This approval request was changed by another operation before this one could be saved.");
		}

		ApprovalRequest updated = ApprovalRequest.reconstitute(request.getId(), request.getProjectId(),
				request.getFeatureFlagId(), request.getEnvironmentId(), request.getBaseConfigVersion(),
				request.getProposedConfig(), request.getStatus(), request.getSubmittedBy(), request.getSubmittedAt(),
				request.getReviewedBy(), request.getReviewedAt(), request.getApprovalComment(),
				request.getRejectionComment(), request.getScheduledAt(), request.getAppliedVersion(),
				request.getCancellationReason(), request.getCancelledBy(), request.getCancelledAt(),
				request.getPersistenceVersion() + 1);
		byId.put(updated.getId(), updated);

		return copyOf(updated);
	}

	@Override
	public void deleteAll() {
		byId.clear();
	}

	private static boolean isActive(ApprovalRequest request) {
		return request.getStatus() == ApprovalStatus.PENDING || request.getStatus() == ApprovalStatus.SCHEDULED;
	}

	private static ApprovalRequest copyOf(ApprovalRequest request) {
		return ApprovalRequest.reconstitute(request.getId(), request.getProjectId(), request.getFeatureFlagId(),
				request.getEnvironmentId(), request.getBaseConfigVersion(), request.getProposedConfig(),
				request.getStatus(), request.getSubmittedBy(), request.getSubmittedAt(), request.getReviewedBy(),
				request.getReviewedAt(), request.getApprovalComment(), request.getRejectionComment(),
				request.getScheduledAt(), request.getAppliedVersion(), request.getCancellationReason(),
				request.getCancelledBy(), request.getCancelledAt(), request.getPersistenceVersion());
	}
}
