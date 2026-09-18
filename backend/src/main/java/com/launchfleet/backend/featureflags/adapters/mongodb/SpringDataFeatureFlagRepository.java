package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Implementation detail of MongoFeatureFlagStore only - never exposed outside this
 * package. The application layer depends on the FeatureFlagStore port, not on this.
 */
interface SpringDataFeatureFlagRepository extends MongoRepository<FeatureFlagDocument, String> {

	List<FeatureFlagDocument> findByProjectId(String projectId);

	Optional<FeatureFlagDocument> findByProjectIdAndKey(String projectId, String key);

	boolean existsByProjectIdAndKey(String projectId, String key);

}
