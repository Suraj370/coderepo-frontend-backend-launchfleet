package com.launchfleet.backend.projects.web;

import com.launchfleet.backend.users.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddProjectMemberRequest(

		@NotBlank @Email String email,

		@NotNull Role role) {
}
