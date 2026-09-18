package com.launchfleet.backend.approvals.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;

/** Mirrors featureflags.application.InMemoryFeatureFlagStore - kept local since that one is package-private there. */
class InMemoryFeatureFlagStore implements FeatureFlagStore {

	private final Map<String, FeatureFlag> byId = new ConcurrentHashMap<>();

	@Override
	public List<FeatureFlag> findByProjectId(String projectId) {
		return byId.values().stream().filter(flag -> flag.getProjectId().equals(projectId)).toList();
	}

	@Override
	public Optional<FeatureFlag> findByProjectIdAndKey(String projectId, String key) {
		return byId.values().stream()
				.filter(flag -> flag.getProjectId().equals(projectId) && flag.getKey().equals(key)).findFirst();
	}

	@Override
	public boolean existsByProjectIdAndKey(String projectId, String key) {
		return findByProjectIdAndKey(projectId, key).isPresent();
	}

	@Override
	public Optional<FeatureFlag> findById(String id) {
		return Optional.ofNullable(byId.get(id));
	}

	@Override
	public FeatureFlag save(FeatureFlag flag) {
		FeatureFlag toStore = flag.getId() == null
				? FeatureFlag.reconstitute(UUID.randomUUID().toString(), flag.getProjectId(), flag.getKey(),
						flag.getName(), flag.getDescription(), flag.getType(), flag.getStatus(),
						new ArrayList<>(flag.getVariants()), flag.getCreatedBy(), flag.getCreatedAt(),
						flag.getUpdatedBy(), flag.getUpdatedAt())
				: flag;
		byId.put(toStore.getId(), toStore);

		return toStore;
	}

	@Override
	public void deleteAll() {
		byId.clear();
	}
}
