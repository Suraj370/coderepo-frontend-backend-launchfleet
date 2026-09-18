package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.environments.Environment;
import com.launchfleet.backend.environments.EnvironmentRepository;
import com.launchfleet.backend.environments.EnvironmentStatus;
import com.launchfleet.backend.featureflags.ports.EnvironmentLookup;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;

/** Wraps the existing (untouched) EnvironmentRepository from the environments module. */
@Component
class MongoEnvironmentLookup implements EnvironmentLookup {

	private final EnvironmentRepository environmentRepository;

	MongoEnvironmentLookup(EnvironmentRepository environmentRepository) {
		this.environmentRepository = environmentRepository;
	}

	@Override
	public List<EnvironmentRef> findByProjectId(String projectId) {
		return environmentRepository.findByProjectId(projectId).stream().map(this::toRef).toList();
	}

	@Override
	public Optional<EnvironmentRef> findByProjectIdAndKey(String projectId, String key) {
		return environmentRepository.findByProjectIdAndKey(projectId, key).map(this::toRef);
	}

	@Override
	public Optional<EnvironmentRef> findById(String id) {
		return environmentRepository.findById(id).map(this::toRef);
	}

	private EnvironmentRef toRef(Environment environment) {
		return new EnvironmentRef(environment.getId(), environment.getKey(),
				environment.getStatus() == EnvironmentStatus.ACTIVE);
	}
}
