package com.launchfleet.backend.projects;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.launchfleet.backend.users.Role;
import com.launchfleet.backend.users.UserPrincipal;

/**
 * Checked fresh on every request from a @PreAuthorize expression (e.g.
 * {@code @projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)})
 * instead of caching roles on the session's GrantedAuthority list, so a
 * membership change takes effect immediately rather than after re-login.
 */
@Service("projectAccess")
public class ProjectAccessService {

	private final ProjectMembershipRepository projectMembershipRepository;

	public ProjectAccessService(ProjectMembershipRepository projectMembershipRepository) {
		this.projectMembershipRepository = projectMembershipRepository;
	}

	public boolean hasRole(Authentication authentication, String projectKey, Role required) {
		if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
			return false;
		}

		return projectMembershipRepository.findByUserIdAndProjectKey(principal.getId(), projectKey)
				.map(membership -> membership.getRole().atLeast(required)).orElse(false);
	}
}
