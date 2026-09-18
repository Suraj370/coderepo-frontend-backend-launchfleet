package com.launchfleet.backend.projects.application;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.projects.ProjectMembership;
import com.launchfleet.backend.projects.ProjectMembershipRepository;
import com.launchfleet.backend.shared.ApiException;
import com.launchfleet.backend.users.Role;
import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserRepository;

/**
 * Grants an existing, active, registered user access to a project. There is no
 * email-invite flow - the email must already belong to a registered account.
 */
@Component
public class AddProjectMember {

	private static final int NOT_FOUND = 404;

	private static final int CONFLICT = 409;

	private final UserRepository userRepository;

	private final ProjectMembershipRepository projectMembershipRepository;

	public AddProjectMember(UserRepository userRepository, ProjectMembershipRepository projectMembershipRepository) {
		this.userRepository = userRepository;
		this.projectMembershipRepository = projectMembershipRepository;
	}

	public ProjectMemberView execute(String projectKey, String email, Role role) {
		User user = userRepository.findByEmailAndActiveTrue(email.toLowerCase())
				.orElseThrow(() -> new ApiException(NOT_FOUND, "USER_NOT_FOUND",
						"No active user is registered with that email."));

		if (projectMembershipRepository.findByUserIdAndProjectKey(user.getId(), projectKey).isPresent()) {
			throw new ApiException(CONFLICT, "ALREADY_A_MEMBER", "This user already has access to this project.");
		}

		ProjectMembership membership = new ProjectMembership();
		membership.setUserId(user.getId());
		membership.setProjectKey(projectKey);
		membership.setRole(role);
		membership.setCreatedAt(Instant.now());
		ProjectMembership saved = projectMembershipRepository.save(membership);

		return new ProjectMemberView(saved, user.getName(), user.getEmail());
	}
}
