package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Implementation detail of MongoFeatureFlagConfigStore only - never exposed outside this package. */
interface SpringDataFeatureFlagConfigRepository extends MongoRepository<FeatureFlagConfigDocument, String> {

	List<FeatureFlagConfigDocument> findByFeatureFlagId(String featureFlagId);

	Optional<FeatureFlagConfigDocument> findByFeatureFlagIdAndEnvironmentId(String featureFlagId,
			String environmentId);

	List<FeatureFlagConfigDocument> findByProjectId(String projectId);

}
