package com.launchfleet.backend.experiments.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;

/** Every "find" returns a fresh copy, never the stored reference - see the identical note on approvals.application's own copy of this fake. */
class InMemoryFeatureFlagConfigStore implements FeatureFlagConfigStore {

	private final Map<String, FeatureFlagConfig> byId = new ConcurrentHashMap<>();

	@Override
	public List<FeatureFlagConfig> findByFeatureFlagId(String featureFlagId) {
		return byId.values().stream().filter(config -> config.getFeatureFlagId().equals(featureFlagId))
				.map(InMemoryFeatureFlagConfigStore::copyOf).toList();
	}

	@Override
	public Optional<FeatureFlagConfig> findByFeatureFlagIdAndEnvironmentId(String featureFlagId,
			String environmentId) {
		return byId.values().stream().filter(config -> config.getFeatureFlagId().equals(featureFlagId)
				&& config.getEnvironmentId().equals(environmentId)).findFirst().map(InMemoryFeatureFlagConfigStore::copyOf);
	}

	@Override
	public List<FeatureFlagConfig> findByProjectId(String projectId) {
		return byId.values().stream().filter(config -> config.getProjectId().equals(projectId))
				.map(InMemoryFeatureFlagConfigStore::copyOf).toList();
	}

	@Override
	public FeatureFlagConfig save(FeatureFlagConfig config) {
		FeatureFlagConfig toStore = config.getId() == null
				? FeatureFlagConfig.reconstitute(UUID.randomUUID().toString(), config.getFeatureFlagId(),
						config.getEnvironmentId(), config.getProjectId(), config.isEnabled(),
						config.getDefaultVariantId(), config.getTargetingRules(), config.getRollout(),
						config.getVersion(), config.getUpdatedBy(), config.getUpdatedAt(), config.getCreatedAt())
				: config;
		byId.put(toStore.getId(), toStore);

		return toStore;
	}

	@Override
	public Optional<FeatureFlagConfig> applyIfCurrentVersion(FeatureFlagConfig desiredState, int expectedVersion) {
		FeatureFlagConfig current = byId.get(desiredState.getId());
		if (current == null || current.getVersion() != expectedVersion) {
			return Optional.empty();
		}

		FeatureFlagConfig updated = FeatureFlagConfig.reconstitute(current.getId(), current.getFeatureFlagId(),
				current.getEnvironmentId(), current.getProjectId(), desiredState.isEnabled(),
				desiredState.getDefaultVariantId(), desiredState.getTargetingRules(), desiredState.getRollout(),
				expectedVersion + 1, desiredState.getUpdatedBy(), desiredState.getUpdatedAt(), current.getCreatedAt());
		byId.put(updated.getId(), updated);

		return Optional.of(updated);
	}

	@Override
	public void deleteAll() {
		byId.clear();
	}

	private static FeatureFlagConfig copyOf(FeatureFlagConfig config) {
		return FeatureFlagConfig.reconstitute(config.getId(), config.getFeatureFlagId(), config.getEnvironmentId(),
				config.getProjectId(), config.isEnabled(), config.getDefaultVariantId(), config.getTargetingRules(),
				config.getRollout(), config.getVersion(), config.getUpdatedBy(), config.getUpdatedAt(),
				config.getCreatedAt());
	}
}
