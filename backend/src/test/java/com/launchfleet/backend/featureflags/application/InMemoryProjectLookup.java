package com.launchfleet.backend.featureflags.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.launchfleet.backend.featureflags.ports.ProjectLookup;
import com.launchfleet.backend.featureflags.ports.ProjectRef;

class InMemoryProjectLookup implements ProjectLookup {

	private final Map<String, ProjectRef> byKey = new HashMap<>();

	ProjectRef addProject(String key) {
		ProjectRef project = new ProjectRef(UUID.randomUUID().toString(), key);
		byKey.put(key, project);

		return project;
	}

	@Override
	public Optional<ProjectRef> findByKey(String key) {
		return Optional.ofNullable(byKey.get(key));
	}
}
