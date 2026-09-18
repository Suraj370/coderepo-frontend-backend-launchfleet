package com.launchfleet.backend.featureflags.ports;

import java.util.List;
import java.util.Optional;

import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;

public interface FeatureFlagConfigStore {

	List<FeatureFlagConfig> findByFeatureFlagId(String featureFlagId);

	Optional<FeatureFlagConfig> findByFeatureFlagIdAndEnvironmentId(String featureFlagId, String environmentId);

	List<FeatureFlagConfig> findByProjectId(String projectId);

	FeatureFlagConfig save(FeatureFlagConfig config);

	/**
	 * Atomically replaces the persisted document only if its stored version still
	 * equals expectedVersion - a MongoDB findAndModify guard against a write racing
	 * between whatever loaded/validated desiredState and this call actually landing
	 * (see the approvals module, Phase 6, the only caller as of this phase). Empty
	 * means the persisted version had already moved past expectedVersion.
	 */
	Optional<FeatureFlagConfig> applyIfCurrentVersion(FeatureFlagConfig desiredState, int expectedVersion);

	void deleteAll();

}
