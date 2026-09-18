package com.launchfleet.backend.featureflags.ports;

import java.util.List;
import java.util.Optional;

import com.launchfleet.backend.featureflags.domain.FeatureFlag;

/**
 * The persistence boundary for FeatureFlag - implemented by an infrastructure
 * adapter (see adapters.mongodb.MongoFeatureFlagStore). The application layer
 * depends only on this interface, never on MongoDB types.
 */
public interface FeatureFlagStore {

	List<FeatureFlag> findByProjectId(String projectId);

	Optional<FeatureFlag> findByProjectIdAndKey(String projectId, String key);

	/** Added for the approvals module (Phase 6): an approval request stores featureFlagId, not a key. */
	Optional<FeatureFlag> findById(String id);

	boolean existsByProjectIdAndKey(String projectId, String key);

	FeatureFlag save(FeatureFlag flag);

	void deleteAll();

}
