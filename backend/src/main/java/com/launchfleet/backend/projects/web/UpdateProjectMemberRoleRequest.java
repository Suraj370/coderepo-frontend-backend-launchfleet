package com.launchfleet.backend.projects.web;

import com.launchfleet.backend.users.Role;

import jakarta.validation.constraints.NotNull;

public record UpdateProjectMemberRoleRequest(

		@NotNull Role role) {
}
