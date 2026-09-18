package com.launchfleet.backend.featureflags.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.launchfleet.backend.featureflags.ports.EnvironmentLookup;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;

class InMemoryEnvironmentLookup implements EnvironmentLookup {

	private final List<EnvironmentRefWithProject> environments = new ArrayList<>();

	private record EnvironmentRefWithProject(String projectId, EnvironmentRef ref) {
	}

	EnvironmentRef addEnvironment(String projectId, String key) {
		EnvironmentRef environment = new EnvironmentRef(UUID.randomUUID().toString(), key, true);
		environments.add(new EnvironmentRefWithProject(projectId, environment));

		return environment;
	}

	void retire(String environmentId) {
		for (int i = 0; i < environments.size(); i++) {
			EnvironmentRefWithProject existing = environments.get(i);
			if (existing.ref().id().equals(environmentId)) {
				environments.set(i, new EnvironmentRefWithProject(existing.projectId(),
						new EnvironmentRef(existing.ref().id(), existing.ref().key(), false)));
				return;
			}
		}
	}

	@Override
	public List<EnvironmentRef> findByProjectId(String projectId) {
		return environments.stream().filter(e -> e.projectId().equals(projectId)).map(EnvironmentRefWithProject::ref)
				.toList();
	}

	@Override
	public Optional<EnvironmentRef> findByProjectIdAndKey(String projectId, String key) {
		return environments.stream()
				.filter(e -> e.projectId().equals(projectId) && e.ref().key().equals(key))
				.map(EnvironmentRefWithProject::ref).findFirst();
	}

	@Override
	public Optional<EnvironmentRef> findById(String id) {
		return environments.stream().filter(e -> e.ref().id().equals(id)).map(EnvironmentRefWithProject::ref)
				.findFirst();
	}
}
