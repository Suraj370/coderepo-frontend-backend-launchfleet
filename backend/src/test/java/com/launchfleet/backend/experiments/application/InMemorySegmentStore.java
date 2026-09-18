package com.launchfleet.backend.experiments.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.ports.SegmentStore;

/**
 * A fake, in-memory SegmentStore for testing experiments use cases without Spring
 * or MongoDB - mirrors featureflags.application's own copy of this fake exactly
 * (that one is package-private there, so it isn't reusable from this package).
 */
class InMemorySegmentStore implements SegmentStore {

	private final Map<String, Segment> byId = new ConcurrentHashMap<>();

	@Override
	public List<Segment> findByProjectId(String projectId) {
		return byId.values().stream().filter(segment -> segment.getProjectId().equals(projectId)).toList();
	}

	@Override
	public Optional<Segment> findByProjectIdAndKey(String projectId, String key) {
		return byId.values().stream()
				.filter(segment -> segment.getProjectId().equals(projectId) && segment.getKey().equals(key))
				.findFirst();
	}

	@Override
	public Optional<Segment> findById(String id) {
		return Optional.ofNullable(byId.get(id));
	}

	@Override
	public boolean existsByProjectIdAndKey(String projectId, String key) {
		return findByProjectIdAndKey(projectId, key).isPresent();
	}

	@Override
	public Segment save(Segment segment) {
		Segment toStore = segment.getId() == null
				? Segment.reconstitute(UUID.randomUUID().toString(), segment.getProjectId(), segment.getKey(),
						segment.getName(), segment.getStatus(), segment.getConditions(), segment.getCreatedBy(),
						segment.getCreatedAt(), segment.getUpdatedBy(), segment.getUpdatedAt())
				: segment;
		byId.put(toStore.getId(), toStore);

		return toStore;
	}

	@Override
	public void deleteAll() {
		byId.clear();
	}
}
