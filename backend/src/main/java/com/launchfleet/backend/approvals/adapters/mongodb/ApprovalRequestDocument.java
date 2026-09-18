package com.launchfleet.backend.approvals.adapters.mongodb;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The one-PENDING-per-scope invariant (locked architecture rule 6) is enforced at the
 * MongoDB level by a partial unique index on (featureFlagId, environmentId), filtered
 * to status="PENDING" only - created programmatically in MongoApprovalRequestStore
 * (Spring Data's @CompoundIndex annotation has no partialFilterExpression attribute),
 * so this is a backstop behind the application-level check in SubmitApprovalRequest,
 * not a replacement for it (a clear error message at request time beats a raw
 * DuplicateKeyException surfacing as a 500).
 */
@Document(collection = "approval_requests")
@CompoundIndex(name = "project_lookup", def = "{'projectId': 1, 'submittedAt': -1}")
class ApprovalRequestDocument {

	@Id
	private String id;

	private String projectId;

	private String featureFlagId;

	private String environmentId;

	private int baseConfigVersion;

	private boolean proposedEnabled;

	private String proposedDefaultVariantId;

	private List<ApprovalTargetingRuleDocument> proposedTargetingRules;

	private ApprovalRolloutDocument proposedRollout;

	private String status;

	private String submittedBy;

	private Instant submittedAt;

	private String reviewedBy;

	private Instant reviewedAt;

	private String approvalComment;

	private String rejectionComment;

	private Instant scheduledAt;

	private Integer appliedVersion;

	private String cancellationReason;

	private String cancelledBy;

	private Instant cancelledAt;

	private int persistenceVersion;

	String getId() {
		return id;
	}

	void setId(String id) {
		this.id = id;
	}

	String getProjectId() {
		return projectId;
	}

	void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	String getFeatureFlagId() {
		return featureFlagId;
	}

	void setFeatureFlagId(String featureFlagId) {
		this.featureFlagId = featureFlagId;
	}

	String getEnvironmentId() {
		return environmentId;
	}

	void setEnvironmentId(String environmentId) {
		this.environmentId = environmentId;
	}

	int getBaseConfigVersion() {
		return baseConfigVersion;
	}

	void setBaseConfigVersion(int baseConfigVersion) {
		this.baseConfigVersion = baseConfigVersion;
	}

	boolean isProposedEnabled() {
		return proposedEnabled;
	}

	void setProposedEnabled(boolean proposedEnabled) {
		this.proposedEnabled = proposedEnabled;
	}

	String getProposedDefaultVariantId() {
		return proposedDefaultVariantId;
	}

	void setProposedDefaultVariantId(String proposedDefaultVariantId) {
		this.proposedDefaultVariantId = proposedDefaultVariantId;
	}

	List<ApprovalTargetingRuleDocument> getProposedTargetingRules() {
		return proposedTargetingRules;
	}

	void setProposedTargetingRules(List<ApprovalTargetingRuleDocument> proposedTargetingRules) {
		this.proposedTargetingRules = proposedTargetingRules;
	}

	ApprovalRolloutDocument getProposedRollout() {
		return proposedRollout;
	}

	void setProposedRollout(ApprovalRolloutDocument proposedRollout) {
		this.proposedRollout = proposedRollout;
	}

	String getStatus() {
		return status;
	}

	void setStatus(String status) {
		this.status = status;
	}

	String getSubmittedBy() {
		return submittedBy;
	}

	void setSubmittedBy(String submittedBy) {
		this.submittedBy = submittedBy;
	}

	Instant getSubmittedAt() {
		return submittedAt;
	}

	void setSubmittedAt(Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	String getReviewedBy() {
		return reviewedBy;
	}

	void setReviewedBy(String reviewedBy) {
		this.reviewedBy = reviewedBy;
	}

	Instant getReviewedAt() {
		return reviewedAt;
	}

	void setReviewedAt(Instant reviewedAt) {
		this.reviewedAt = reviewedAt;
	}

	String getApprovalComment() {
		return approvalComment;
	}

	void setApprovalComment(String approvalComment) {
		this.approvalComment = approvalComment;
	}

	String getRejectionComment() {
		return rejectionComment;
	}

	void setRejectionComment(String rejectionComment) {
		this.rejectionComment = rejectionComment;
	}

	Instant getScheduledAt() {
		return scheduledAt;
	}

	void setScheduledAt(Instant scheduledAt) {
		this.scheduledAt = scheduledAt;
	}

	Integer getAppliedVersion() {
		return appliedVersion;
	}

	void setAppliedVersion(Integer appliedVersion) {
		this.appliedVersion = appliedVersion;
	}

	String getCancellationReason() {
		return cancellationReason;
	}

	void setCancellationReason(String cancellationReason) {
		this.cancellationReason = cancellationReason;
	}

	String getCancelledBy() {
		return cancelledBy;
	}

	void setCancelledBy(String cancelledBy) {
		this.cancelledBy = cancelledBy;
	}

	Instant getCancelledAt() {
		return cancelledAt;
	}

	void setCancelledAt(Instant cancelledAt) {
		this.cancelledAt = cancelledAt;
	}

	int getPersistenceVersion() {
		return persistenceVersion;
	}

	void setPersistenceVersion(int persistenceVersion) {
		this.persistenceVersion = persistenceVersion;
	}
}
