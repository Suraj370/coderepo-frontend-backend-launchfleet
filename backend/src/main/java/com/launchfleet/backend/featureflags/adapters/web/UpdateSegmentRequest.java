package com.launchfleet.backend.featureflags.adapters.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record UpdateSegmentRequest(

		@NotBlank String name,

		@NotEmpty List<@Valid ConditionRequest> conditions) {
}
