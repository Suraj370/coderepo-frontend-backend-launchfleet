package com.launchfleet.backend.experiments.application;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.launchfleet.backend.experiments.domain.ExperimentAssignment;
import com.launchfleet.backend.experiments.ports.ExperimentAssignmentStore;

/** Mirrors MongoExperimentAssignmentStore's first-write-wins semantics using a single synchronized map operation instead of a real unique index. */
class InMemoryExperimentAssignmentStore implements ExperimentAssignmentStore {

	private final Map<String, ExperimentAssignment> byExperimentAndUser = new ConcurrentHashMap<>();

	private static String keyFor(String experimentId, String userKey) {
		return experimentId + "::" + userKey;
	}

	@Override
	public Optional<ExperimentAssignment> findByExperimentIdAndUserKey(String experimentId, String userKey) {
		return Optional.ofNullable(byExperimentAndUser.get(keyFor(experimentId, userKey)));
	}

	@Override
	public ExperimentAssignment getOrCreate(ExperimentAssignment candidate) {
		ExperimentAssignment toStore = ExperimentAssignment.reconstitute(UUID.randomUUID().toString(),
				candidate.getExperimentId(), candidate.getUserKey(), candidate.getVariantId(),
				candidate.getAssignedAt());

		ExperimentAssignment winner = byExperimentAndUser.putIfAbsent(
				keyFor(candidate.getExperimentId(), candidate.getUserKey()), toStore);

		return winner == null ? toStore : winner;
	}

	@Override
	public long countByExperimentId(String experimentId) {
		return byExperimentAndUser.values().stream().filter(a -> a.getExperimentId().equals(experimentId)).count();
	}

	@Override
	public long countByExperimentIdAndVariantId(String experimentId, String variantId) {
		return byExperimentAndUser.values().stream()
				.filter(a -> a.getExperimentId().equals(experimentId) && a.getVariantId().equals(variantId)).count();
	}

	@Override
	public void deleteAll() {
		byExperimentAndUser.clear();
	}
}
