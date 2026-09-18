package com.launchfleet.backend.featureflags.adapters.web;

import jakarta.validation.constraints.NotBlank;

public record VariantRequest(

		@NotBlank String key,

		@NotBlank String name,

		Object value) {
}
