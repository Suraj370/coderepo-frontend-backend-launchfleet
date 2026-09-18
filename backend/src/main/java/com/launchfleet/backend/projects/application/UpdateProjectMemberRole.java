package com.launchfleet.backend.projects.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.projects.ProjectMembership;
import com.launchfleet.backend.projects.ProjectMembershipRepository;
import com.launchfleet.backend.shared.ApiException;
import com.launchfleet.backend.users.Role;
import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserRepository;

/** A project can never be left with zero ADMINs - demoting the last one is rejected. */
@Component
public class UpdateProjectMemberRole {

	private static final int NOT_FOUND = 404;

	private static final int CONFLICT = 409;

	private final ProjectMembershipRepository projectMembershipRepository;

	private final UserRepository userRepository;

	public UpdateProjectMemberRole(ProjectMembershipRepository projectMembershipRepository,
			UserRepository userRepository) {
		this.projectMembershipRepository = projectMembershipRepository;
		this.userRepository = userRepository;
	}

	public ProjectMemberView execute(String projectKey, String membershipId, Role newRole) {
		ProjectMembership membership = projectMembershipRepository.findByIdAndProjectKey(membershipId, projectKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "MEMBERSHIP_NOT_FOUND",
						"No membership with that id in this project."));

		if (membership.getRole() == Role.ADMIN && newRole != Role.ADMIN
				&& projectMembershipRepository.countByProjectKeyAndRole(projectKey, Role.ADMIN) <= 1) {
			throw new ApiException(CONFLICT, "LAST_ADMIN", "A project must always have at least one ADMIN.");
		}

		membership.setRole(newRole);
		ProjectMembership saved = projectMembershipRepository.save(membership);
		User user = userRepository.findById(saved.getUserId())
				.orElseThrow(() -> new IllegalStateException("Membership references a missing user: " + saved.getUserId()));

		return new ProjectMemberView(saved, user.getName(), user.getEmail());
	}
}
