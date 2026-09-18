package com.launchfleet.backend.environments.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.environments.Environment;
import com.launchfleet.backend.environments.EnvironmentRepository;

@Component
public class ListEnvironments {

	private final EnvironmentRepository environmentRepository;

	public ListEnvironments(EnvironmentRepository environmentRepository) {
		this.environmentRepository = environmentRepository;
	}

	public List<Environment> execute(String projectId) {
		return environmentRepository.findByProjectId(projectId);
	}
}
