package com.launchfleet.backend.featureflags.adapters.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

/** Shared shape for both adding and updating a targeting rule - identical fields either way. */
public record TargetingRuleRequest(

		int priority,

		@NotEmpty List<@Valid ConditionRequest> conditions,

		@NotBlank String variantId) {
}
