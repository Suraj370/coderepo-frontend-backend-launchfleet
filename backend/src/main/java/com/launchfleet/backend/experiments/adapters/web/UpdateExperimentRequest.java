package com.launchfleet.backend.experiments.adapters.web;

import java.util.List;

import com.launchfleet.backend.featureflags.adapters.web.AllocationRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * DRAFT-only, whole-configuration replacement (mirrors RolloutRequest's PUT-replace
 * semantics) - reuses the existing AllocationRequest record from featureflags/
 * rather than a second percentage-pair DTO (locked decision 6: no second
 * percentage representation).
 */
public record UpdateExperimentRequest(

		@NotBlank String name,

		String description,

		List<@Valid AllocationRequest> allocation,

		String conversionEventName) {
}
