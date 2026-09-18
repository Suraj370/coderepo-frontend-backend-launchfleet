package com.launchfleet.backend.projects.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.projects.ProjectMembership;
import com.launchfleet.backend.projects.ProjectMembershipRepository;
import com.launchfleet.backend.shared.ApiException;
import com.launchfleet.backend.users.Role;

/** A project can never be left with zero ADMINs - removing the last one is rejected. */
@Component
public class RemoveProjectMember {

	private static final int NOT_FOUND = 404;

	private static final int CONFLICT = 409;

	private final ProjectMembershipRepository projectMembershipRepository;

	public RemoveProjectMember(ProjectMembershipRepository projectMembershipRepository) {
		this.projectMembershipRepository = projectMembershipRepository;
	}

	public void execute(String projectKey, String membershipId) {
		ProjectMembership membership = projectMembershipRepository.findByIdAndProjectKey(membershipId, projectKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "MEMBERSHIP_NOT_FOUND",
						"No membership with that id in this project."));

		if (membership.getRole() == Role.ADMIN
				&& projectMembershipRepository.countByProjectKeyAndRole(projectKey, Role.ADMIN) <= 1) {
			throw new ApiException(CONFLICT, "LAST_ADMIN", "A project must always have at least one ADMIN.");
		}

		projectMembershipRepository.delete(membership);
	}
}
