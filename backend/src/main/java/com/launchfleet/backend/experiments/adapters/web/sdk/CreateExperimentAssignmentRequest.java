package com.launchfleet.backend.experiments.adapters.web.sdk;

import jakarta.validation.constraints.NotBlank;

public record CreateExperimentAssignmentRequest(@NotBlank String userKey) {
}
