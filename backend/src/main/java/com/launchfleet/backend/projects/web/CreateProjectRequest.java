package com.launchfleet.backend.projects.web;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(

		@NotBlank String key,

		@NotBlank String name) {
}
