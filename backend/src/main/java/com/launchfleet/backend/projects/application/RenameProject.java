package com.launchfleet.backend.projects.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.projects.Project;
import com.launchfleet.backend.projects.ProjectRepository;
import com.launchfleet.backend.shared.ApiException;

/** The project's key is immutable once created - only the display name can change. */
@Component
public class RenameProject {

	private static final int BAD_REQUEST = 400;

	private final ProjectRepository projectRepository;

	public RenameProject(ProjectRepository projectRepository) {
		this.projectRepository = projectRepository;
	}

	public Project execute(Project project, String name) {
		if (name == null || name.isBlank()) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", "name is required.");
		}

		project.setName(name);

		return projectRepository.save(project);
	}
}
