package com.launchfleet.backend.experiments.adapters.web;

import jakarta.validation.constraints.NotBlank;

public record CreateExperimentRequest(

		@NotBlank String environmentKey,

		@NotBlank String flagKey,

		@NotBlank String key,

		@NotBlank String name,

		String description) {
}
