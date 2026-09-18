package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.ports.ProjectLookup;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.projects.ProjectRepository;

/**
 * Wraps the existing (untouched) ProjectRepository from the projects module - Project
 * itself isn't part of this refactor, only how the featureflags module depends on it.
 */
@Component
class MongoProjectLookup implements ProjectLookup {

	private final ProjectRepository projectRepository;

	MongoProjectLookup(ProjectRepository projectRepository) {
		this.projectRepository = projectRepository;
	}

	@Override
	public Optional<ProjectRef> findByKey(String key) {
		return projectRepository.findByKey(key).map(project -> new ProjectRef(project.getId(), project.getKey()));
	}
}
