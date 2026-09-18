package com.launchfleet.backend.approvals.adapters.mongodb;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Implementation detail of MongoApprovalRequestStore only - never exposed outside this package. */
interface SpringDataApprovalRequestRepository extends MongoRepository<ApprovalRequestDocument, String> {

	List<ApprovalRequestDocument> findByProjectIdOrderBySubmittedAtDesc(String projectId);

	Optional<ApprovalRequestDocument> findByFeatureFlagIdAndEnvironmentIdAndStatus(String featureFlagId,
			String environmentId, String status);

	List<ApprovalRequestDocument> findByFeatureFlagIdAndStatusIn(String featureFlagId, List<String> statuses);

	List<ApprovalRequestDocument> findByEnvironmentIdAndStatusIn(String environmentId, List<String> statuses);

	List<ApprovalRequestDocument> findByStatusAndScheduledAtLessThanEqual(String status, Instant now);

}
