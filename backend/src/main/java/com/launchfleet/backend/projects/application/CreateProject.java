package com.launchfleet.backend.projects.application;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.projects.Project;
import com.launchfleet.backend.projects.ProjectMembership;
import com.launchfleet.backend.projects.ProjectMembershipRepository;
import com.launchfleet.backend.projects.ProjectRepository;
import com.launchfleet.backend.shared.ApiException;
import com.launchfleet.backend.users.Role;

/**
 * Creates a project and, in the same operation, grants its creator ADMIN
 * membership - otherwise a brand-new project would be immediately unreachable
 * by anyone (ProjectAccessService.hasRole has nothing to find).
 */
@Component
public class CreateProject {

	private static final int BAD_REQUEST = 400;

	private static final int CONFLICT = 409;

	private final ProjectRepository projectRepository;

	private final ProjectMembershipRepository projectMembershipRepository;

	public CreateProject(ProjectRepository projectRepository,
			ProjectMembershipRepository projectMembershipRepository) {
		this.projectRepository = projectRepository;
		this.projectMembershipRepository = projectMembershipRepository;
	}

	public Project execute(String key, String name, String creatorUserId) {
		if (key == null || key.isBlank()) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", "key is required.");
		}
		if (name == null || name.isBlank()) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", "name is required.");
		}
		if (projectRepository.findByKey(key).isPresent()) {
			throw new ApiException(CONFLICT, "PROJECT_KEY_TAKEN", "A project with this key already exists.");
		}

		Project project = new Project();
		project.setKey(key);
		project.setName(name);
		project.setCreatedAt(Instant.now());
		Project saved = projectRepository.save(project);

		ProjectMembership membership = new ProjectMembership();
		membership.setUserId(creatorUserId);
		membership.setProjectKey(key);
		membership.setRole(Role.ADMIN);
		membership.setCreatedAt(Instant.now());
		projectMembershipRepository.save(membership);

		return saved;
	}
}
