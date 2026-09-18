package com.launchfleet.backend.approvals.domain;

import java.time.Instant;

/**
 * The approval workflow's aggregate (Phase 6). Carries the COMPLETE proposed
 * configuration snapshot directly (see ProposedConfig) rather than a separate
 * proposal aggregate that exists only to hold it (locked architecture rule 1) - this
 * class both IS the proposal and tracks its review/application lifecycle.
 *
 * Every status transition is a method here, and every method enforces its own
 * preconditions (current status, two-person-approval, non-blank rejection comment,
 * future-only scheduling) by throwing IllegalStateException/IllegalArgumentException
 * - callers (the application layer) translate those into the appropriate ApiException.
 * This is what "keep transition logic centralized" (locked architecture rule 26)
 * means in practice: no use case, controller, or Mongo adapter ever assigns `status`
 * directly.
 *
 * The only valid transitions are exactly:
 * <pre>
 * PENDING   -> REJECTED | CANCELLED | APPROVED
 * APPROVED  -> APPLIED | SCHEDULED
 * SCHEDULED -> APPLIED | CANCELLED | FAILED
 * </pre>
 * APPROVED is never the FINAL status of an HTTP request in this phase - the immediate-
 * approve and approve-and-schedule use cases each call approve() and then, in the same
 * call, applyNow()/scheduleFor() before ever persisting - so a caller only ever
 * observes PENDING, REJECTED, CANCELLED, SCHEDULED, APPLIED, or FAILED. approve() still
 * exists as its own transition (rather than being inlined into applyNow/scheduleFor)
 * so the "not your own submission" check has exactly one implementation shared by both
 * flows.
 *
 * Immutable once submitted in every field except its own lifecycle bookkeeping
 * (status, reviewer, timestamps, comments, schedule, applied version, cancellation) -
 * proposedConfig itself never changes after propose() (locked architecture rule 2). A
 * different proposal is always a new ApprovalRequest.
 *
 * persistenceVersion is a pure storage-adapter concern - the same idea as
 * FeatureFlagConfig.version, applied here for the same reason: two concurrent
 * operations (e.g. an ADMIN's approve and a different ADMIN's reject, or two
 * ScheduledApprovalPoller executions) can each load this same PENDING/SCHEDULED
 * request, each perform a domain-valid transition on their own in-memory copy, and
 * then race to persist. ApprovalRequestStore.save() conditions its write on this
 * field (see MongoApprovalRequestStore) so only the operation that observed the
 * CURRENT persisted state can ever commit - a stale operation's save() fails with
 * ApprovalRequestConflictException instead of silently overwriting the winner. No
 * domain method here ever touches it; only the store does, on successful save.
 */
public final class ApprovalRequest {

	private final String id;

	private final String projectId;

	private final String featureFlagId;

	private final String environmentId;

	private final int baseConfigVersion;

	private final ProposedConfig proposedConfig;

	private ApprovalStatus status;

	private final String submittedBy;

	private final Instant submittedAt;

	private String reviewedBy;

	private Instant reviewedAt;

	private String approvalComment;

	private String rejectionComment;

	private Instant scheduledAt;

	private Integer appliedVersion;

	private CancellationReason cancellationReason;

	private String cancelledBy;

	private Instant cancelledAt;

	private final int persistenceVersion;

	private ApprovalRequest(String id, String projectId, String featureFlagId, String environmentId,
			int baseConfigVersion, ProposedConfig proposedConfig, ApprovalStatus status, String submittedBy,
			Instant submittedAt, String reviewedBy, Instant reviewedAt, String approvalComment,
			String rejectionComment, Instant scheduledAt, Integer appliedVersion,
			CancellationReason cancellationReason, String cancelledBy, Instant cancelledAt, int persistenceVersion) {
		this.id = id;
		this.projectId = projectId;
		this.featureFlagId = featureFlagId;
		this.environmentId = environmentId;
		this.baseConfigVersion = baseConfigVersion;
		this.proposedConfig = proposedConfig;
		this.status = status;
		this.submittedBy = submittedBy;
		this.submittedAt = submittedAt;
		this.reviewedBy = reviewedBy;
		this.reviewedAt = reviewedAt;
		this.approvalComment = approvalComment;
		this.rejectionComment = rejectionComment;
		this.scheduledAt = scheduledAt;
		this.appliedVersion = appliedVersion;
		this.cancellationReason = cancellationReason;
		this.cancelledBy = cancelledBy;
		this.cancelledAt = cancelledAt;
		this.persistenceVersion = persistenceVersion;
	}

	/** A brand-new proposal, always PENDING, always based on baseConfigVersion (see the application layer for how that's captured). */
	public static ApprovalRequest propose(String projectId, String featureFlagId, String environmentId,
			int baseConfigVersion, ProposedConfig proposedConfig, String submittedBy) {
		if (projectId == null || projectId.isBlank()) {
			throw new IllegalArgumentException("projectId is required.");
		}
		if (featureFlagId == null || featureFlagId.isBlank()) {
			throw new IllegalArgumentException("featureFlagId is required.");
		}
		if (environmentId == null || environmentId.isBlank()) {
			throw new IllegalArgumentException("environmentId is required.");
		}
		if (proposedConfig == null) {
			throw new IllegalArgumentException("proposedConfig is required.");
		}
		if (submittedBy == null || submittedBy.isBlank()) {
			throw new IllegalArgumentException("submittedBy is required.");
		}

		return new ApprovalRequest(null, projectId, featureFlagId, environmentId, baseConfigVersion, proposedConfig,
				ApprovalStatus.PENDING, submittedBy, Instant.now(), null, null, null, null, null, null, null, null,
				null, 0);
	}

	/** Rehydrates a request already known to be valid, exactly as persisted - storage adapters only. */
	public static ApprovalRequest reconstitute(String id, String projectId, String featureFlagId,
			String environmentId, int baseConfigVersion, ProposedConfig proposedConfig, ApprovalStatus status,
			String submittedBy, Instant submittedAt, String reviewedBy, Instant reviewedAt, String approvalComment,
			String rejectionComment, Instant scheduledAt, Integer appliedVersion,
			CancellationReason cancellationReason, String cancelledBy, Instant cancelledAt, int persistenceVersion) {
		return new ApprovalRequest(id, projectId, featureFlagId, environmentId, baseConfigVersion, proposedConfig,
				status, submittedBy, submittedAt, reviewedBy, reviewedAt, approvalComment, rejectionComment,
				scheduledAt, appliedVersion, cancellationReason, cancelledBy, cancelledAt, persistenceVersion);
	}

	/**
	 * PENDING -> APPROVED. Two-person approval is mandatory: reviewerId must differ
	 * from submittedBy even if the reviewer is an ADMIN (locked architecture rule 5) -
	 * this is the one place that invariant is enforced, shared by both the immediate-
	 * apply and approve-and-schedule flows.
	 */
	public void approve(String reviewerId, String approvalComment) {
		requireStatus(ApprovalStatus.PENDING, "approve");
		requireDifferentReviewer(reviewerId);

		this.status = ApprovalStatus.APPROVED;
		this.reviewedBy = reviewerId;
		this.reviewedAt = Instant.now();
		this.approvalComment = approvalComment;
	}

	/** APPROVED -> APPLIED. The caller (application layer) has already performed the stale/retirement checks and the actual write. */
	public void applyNow(int appliedVersion) {
		requireStatus(ApprovalStatus.APPROVED, "apply");

		this.status = ApprovalStatus.APPLIED;
		this.appliedVersion = appliedVersion;
	}

	/** APPROVED -> SCHEDULED. scheduledAt must be strictly in the future relative to `now` - no minimum lead time otherwise. */
	public void scheduleFor(Instant scheduledAt, Instant now) {
		requireStatus(ApprovalStatus.APPROVED, "schedule");
		if (scheduledAt == null) {
			throw new IllegalArgumentException("scheduledAt is required.");
		}
		if (!scheduledAt.isAfter(now)) {
			throw new IllegalArgumentException("scheduledAt must be strictly in the future.");
		}

		this.status = ApprovalStatus.SCHEDULED;
		this.scheduledAt = scheduledAt;
	}

	/** SCHEDULED -> APPLIED, at the scheduled time (see ScheduledApprovalPoller/ApplyDueScheduledApprovals). */
	public void applyScheduled(int appliedVersion) {
		requireStatus(ApprovalStatus.SCHEDULED, "apply");

		this.status = ApprovalStatus.APPLIED;
		this.appliedVersion = appliedVersion;
	}

	/** SCHEDULED -> FAILED. No automatic retry (locked architecture rule 13) - this status is terminal. */
	public void fail() {
		requireStatus(ApprovalStatus.SCHEDULED, "fail");

		this.status = ApprovalStatus.FAILED;
	}

	/**
	 * PENDING -> REJECTED. Same two-person rule as approve(): the submitter can never
	 * reject their own request. A non-blank rejectionComment is mandatory.
	 */
	public void reject(String reviewerId, String rejectionComment) {
		requireStatus(ApprovalStatus.PENDING, "reject");
		requireDifferentReviewer(reviewerId);
		if (rejectionComment == null || rejectionComment.isBlank()) {
			throw new IllegalArgumentException("rejectionComment is required.");
		}

		this.status = ApprovalStatus.REJECTED;
		this.reviewedBy = reviewerId;
		this.reviewedAt = Instant.now();
		this.rejectionComment = rejectionComment;
	}

	/**
	 * PENDING or SCHEDULED -> CANCELLED. Who is allowed to call this (the submitter for
	 * either status, an ADMIN only for SCHEDULED) is an authorization decision made by
	 * the application layer (CancelApprovalRequest), not here - this method only
	 * enforces the STATUS precondition, matching how every other transition in this
	 * class stays agnostic of RBAC.
	 *
	 * Also accepts the transient APPROVED status: approve() always leads straight into
	 * either applyNow()/scheduleFor() or - if the proposal turns out to be stale or its
	 * flag/environment was retired in the interim - straight into this cancel() call,
	 * all within the same use case call before anything is persisted (see
	 * ApproveApprovalRequest/ScheduleApprovalRequest). APPROVED is never durably
	 * observable through the API either way (see this class's own Javadoc), so this
	 * does not open a new caller-reachable transition.
	 */
	public void cancel(String cancelledBy, CancellationReason reason) {
		if (status != ApprovalStatus.PENDING && status != ApprovalStatus.SCHEDULED
				&& status != ApprovalStatus.APPROVED) {
			throw new IllegalStateException(
					"Only a PENDING, SCHEDULED, or just-APPROVED request can be cancelled (was " + status + ").");
		}
		if (reason == null) {
			throw new IllegalArgumentException("cancellationReason is required.");
		}

		this.status = ApprovalStatus.CANCELLED;
		this.cancellationReason = reason;
		this.cancelledBy = cancelledBy;
		this.cancelledAt = Instant.now();
	}

	private void requireStatus(ApprovalStatus expected, String action) {
		if (status != expected) {
			throw new IllegalStateException(
					"Cannot " + action + " a request that is " + status + " (expected " + expected + ").");
		}
	}

	private void requireDifferentReviewer(String reviewerId) {
		if (reviewerId == null || reviewerId.isBlank()) {
			throw new IllegalArgumentException("reviewerId is required.");
		}
		if (reviewerId.equals(submittedBy)) {
			throw new IllegalStateException("The submitter cannot review their own request.");
		}
	}

	public String getId() {
		return id;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getFeatureFlagId() {
		return featureFlagId;
	}

	public String getEnvironmentId() {
		return environmentId;
	}

	public int getBaseConfigVersion() {
		return baseConfigVersion;
	}

	public ProposedConfig getProposedConfig() {
		return proposedConfig;
	}

	public ApprovalStatus getStatus() {
		return status;
	}

	public String getSubmittedBy() {
		return submittedBy;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public String getReviewedBy() {
		return reviewedBy;
	}

	public Instant getReviewedAt() {
		return reviewedAt;
	}

	public String getApprovalComment() {
		return approvalComment;
	}

	public String getRejectionComment() {
		return rejectionComment;
	}

	public Instant getScheduledAt() {
		return scheduledAt;
	}

	public Integer getAppliedVersion() {
		return appliedVersion;
	}

	public CancellationReason getCancellationReason() {
		return cancellationReason;
	}

	public String getCancelledBy() {
		return cancelledBy;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	/** Storage-adapter concern only - see this class's Javadoc. Never inspected by domain logic or use cases. */
	public int getPersistenceVersion() {
		return persistenceVersion;
	}
}
