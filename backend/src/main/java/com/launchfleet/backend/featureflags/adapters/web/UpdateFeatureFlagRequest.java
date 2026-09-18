package com.launchfleet.backend.featureflags.adapters.web;

import jakarta.validation.constraints.NotBlank;

public record UpdateFeatureFlagRequest(

		@NotBlank String name,

		String description) {
}
