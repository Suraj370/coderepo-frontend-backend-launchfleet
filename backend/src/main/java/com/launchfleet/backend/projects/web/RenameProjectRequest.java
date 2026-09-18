package com.launchfleet.backend.projects.web;

import jakarta.validation.constraints.NotBlank;

public record RenameProjectRequest(

		@NotBlank String name) {
}
