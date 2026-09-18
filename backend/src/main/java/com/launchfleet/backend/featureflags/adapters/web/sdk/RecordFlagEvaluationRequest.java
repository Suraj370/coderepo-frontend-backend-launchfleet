package com.launchfleet.backend.featureflags.adapters.web.sdk;

import jakarta.validation.constraints.NotBlank;

public record RecordFlagEvaluationRequest(

		@NotBlank String userKey,

		@NotBlank String variantId) {
}
