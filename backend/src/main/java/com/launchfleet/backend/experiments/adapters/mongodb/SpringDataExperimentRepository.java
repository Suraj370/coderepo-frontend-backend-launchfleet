package com.launchfleet.backend.experiments.adapters.mongodb;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Implementation detail of MongoExperimentStore only - never exposed outside this package. */
interface SpringDataExperimentRepository extends MongoRepository<ExperimentDocument, String> {

	Optional<ExperimentDocument> findByProjectIdAndKey(String projectId, String key);

	List<ExperimentDocument> findByProjectId(String projectId);

	List<ExperimentDocument> findByFeatureFlagIdAndStatusIn(String featureFlagId, List<String> statuses);

	List<ExperimentDocument> findByEnvironmentIdAndStatusIn(String environmentId, List<String> statuses);

}
