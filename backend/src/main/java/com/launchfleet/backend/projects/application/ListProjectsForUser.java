package com.launchfleet.backend.projects.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.projects.ProjectMembershipRepository;
import com.launchfleet.backend.projects.ProjectRepository;

@Component
public class ListProjectsForUser {

	private final ProjectMembershipRepository projectMembershipRepository;

	private final ProjectRepository projectRepository;

	public ListProjectsForUser(ProjectMembershipRepository projectMembershipRepository,
			ProjectRepository projectRepository) {
		this.projectMembershipRepository = projectMembershipRepository;
		this.projectRepository = projectRepository;
	}

	public List<ProjectMembershipView> execute(String userId) {
		return projectMembershipRepository.findByUserId(userId).stream()
				.map(membership -> new ProjectMembershipView(
						projectRepository.findByKey(membership.getProjectKey())
								.orElseThrow(() -> new IllegalStateException(
										"Membership references a project that no longer exists: "
												+ membership.getProjectKey())),
						membership.getRole()))
				.toList();
	}
}
