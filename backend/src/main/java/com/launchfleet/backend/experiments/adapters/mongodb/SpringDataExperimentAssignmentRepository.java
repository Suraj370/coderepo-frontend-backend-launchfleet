package com.launchfleet.backend.experiments.adapters.mongodb;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Implementation detail of MongoExperimentAssignmentStore only - never exposed outside this package. */
interface SpringDataExperimentAssignmentRepository extends MongoRepository<ExperimentAssignmentDocument, String> {

	Optional<ExperimentAssignmentDocument> findByExperimentIdAndUserKey(String experimentId, String userKey);

	long countByExperimentId(String experimentId);

	long countByExperimentIdAndVariantId(String experimentId, String variantId);

}
