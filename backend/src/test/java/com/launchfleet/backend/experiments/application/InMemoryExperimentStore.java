package com.launchfleet.backend.experiments.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.experiments.domain.ExperimentStatus;
import com.launchfleet.backend.experiments.ports.ExperimentConflictException;
import com.launchfleet.backend.experiments.ports.ExperimentStore;
import com.launchfleet.backend.shared.ApiException;

/** A fake, in-memory ExperimentStore - mirrors the Mongo adapter's optimistic-version-check and (projectId, key) uniqueness semantics. Every "find" returns a fresh copy, never the stored reference. */
class InMemoryExperimentStore implements ExperimentStore {

	private static final int CONFLICT = 409;

	private final Map<String, Experiment> byId = new ConcurrentHashMap<>();

	@Override
	public Optional<Experiment> findById(String id) {
		return Optional.ofNullable(byId.get(id)).map(InMemoryExperimentStore::copyOf);
	}

	@Override
	public Optional<Experiment> findByProjectIdAndKey(String projectId, String key) {
		return byId.values().stream().filter(e -> e.getProjectId().equals(projectId) && e.getKey().equals(key))
				.findFirst().map(InMemoryExperimentStore::copyOf);
	}

	@Override
	public List<Experiment> findByProjectId(String projectId) {
		return byId.values().stream().filter(e -> e.getProjectId().equals(projectId))
				.map(InMemoryExperimentStore::copyOf).toList();
	}

	@Override
	public List<Experiment> findActiveByFeatureFlagId(String featureFlagId) {
		return byId.values().stream().filter(e -> e.getFeatureFlagId().equals(featureFlagId) && isActive(e))
				.map(InMemoryExperimentStore::copyOf).toList();
	}

	@Override
	public List<Experiment> findActiveByEnvironmentId(String environmentId) {
		return byId.values().stream().filter(e -> e.getEnvironmentId().equals(environmentId) && isActive(e))
				.map(InMemoryExperimentStore::copyOf).toList();
	}

	@Override
	public synchronized Experiment save(Experiment experiment) {
		if (experiment.getId() == null) {
			boolean keyTaken = byId.values().stream().anyMatch(
					e -> e.getProjectId().equals(experiment.getProjectId()) && e.getKey().equals(experiment.getKey()));
			if (keyTaken) {
				throw new ApiException(CONFLICT, "EXPERIMENT_KEY_TAKEN",
						"An experiment with this key already exists in this project.");
			}

			Experiment toStore = Experiment.reconstitute(UUID.randomUUID().toString(), experiment.getProjectId(),
					experiment.getEnvironmentId(), experiment.getFeatureFlagId(), experiment.getKey(),
					experiment.getName(), experiment.getDescription(), experiment.getAllocation(),
					experiment.getConversionEventName(), experiment.getStatus(), experiment.getVersion(),
					experiment.getCreatedBy(), experiment.getCreatedAt(), experiment.getUpdatedBy(),
					experiment.getUpdatedAt());
			byId.put(toStore.getId(), toStore);

			return copyOf(toStore);
		}

		Experiment current = byId.get(experiment.getId());
		if (current == null || current.getVersion() != experiment.getVersion() - 1) {
			throw new ExperimentConflictException(
					"This experiment was changed by another operation before this one could be saved.");
		}

		byId.put(experiment.getId(), experiment);

		return copyOf(experiment);
	}

	@Override
	public void deleteAll() {
		byId.clear();
	}

	private static boolean isActive(Experiment experiment) {
		return experiment.getStatus() == ExperimentStatus.DRAFT || experiment.getStatus() == ExperimentStatus.RUNNING;
	}

	private static Experiment copyOf(Experiment experiment) {
		return Experiment.reconstitute(experiment.getId(), experiment.getProjectId(), experiment.getEnvironmentId(),
				experiment.getFeatureFlagId(), experiment.getKey(), experiment.getName(), experiment.getDescription(),
				experiment.getAllocation(), experiment.getConversionEventName(), experiment.getStatus(),
				experiment.getVersion(), experiment.getCreatedBy(), experiment.getCreatedAt(),
				experiment.getUpdatedBy(), experiment.getUpdatedAt());
	}
}
