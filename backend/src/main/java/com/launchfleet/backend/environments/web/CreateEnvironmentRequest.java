package com.launchfleet.backend.environments.web;

import jakarta.validation.constraints.NotBlank;

public record CreateEnvironmentRequest(

		@NotBlank String key,

		@NotBlank String name) {
}
