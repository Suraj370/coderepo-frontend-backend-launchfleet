package com.launchfleet.backend.featureflags.adapters.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AllocationRequest(

		@NotBlank String variantId,

		@NotNull Integer percentage) {
}
