package com.launchfleet.backend.featureflags.adapters.web.sdk;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.launchfleet.backend.featureflags.application.RecordFlagEvaluation;
import com.launchfleet.backend.featureflags.domain.FlagEvaluationEvent;
import com.launchfleet.backend.sdk.SdkCredential;
import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Records that a flag was evaluated for one end-user - a counter only, feeding the
 * dashboard's evaluation-volume chart. Actual flag evaluation happens entirely
 * client-side from the SDK's cached configuration (see SdkConfigurationResource);
 * calling this endpoint is optional and purely for analytics. Lives under the
 * existing sdkFilterChain, mirroring ExperimentEventResource exactly: project/
 * environment scope comes only from the authenticated SdkCredential, never from a
 * path/query/body parameter.
 */
@RestController
@RequestMapping("/api/v1/sdk/flags")
@Tag(name = "SDK", description = "Endpoints called by SDK_SERVER-authenticated customer application code: configuration distribution and experiment/flag-evaluation event recording. Project/environment scope always comes from the authenticated credential, never from a request parameter.")
@SecurityRequirement(name = OpenApiConfig.SDK_SERVER_SCHEME)
public class FlagEvaluationResource {

	private final RecordFlagEvaluation recordFlagEvaluation;

	public FlagEvaluationResource(RecordFlagEvaluation recordFlagEvaluation) {
		this.recordFlagEvaluation = recordFlagEvaluation;
	}

	@PostMapping("/{flagKey}/evaluations")
	@Operation(summary = "Record a flag evaluation", description = "A counter only - actual evaluation happens client-side from the cached SDK configuration. Calling this is optional and purely feeds the dashboard's evaluation-volume chart.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Recorded."),
			@ApiResponse(responseCode = "400", description = "Validation error (blank userKey/variantId).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "Missing/invalid/expired/inactive SDK_SERVER credential.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "A valid credential that is not SDK_SERVER.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No flag with that key in the credential's project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> recordEvaluation(@AuthenticationPrincipal SdkCredential credential,
			@PathVariable String flagKey, @Valid @RequestBody RecordFlagEvaluationRequest request) {
		FlagEvaluationEvent event = recordFlagEvaluation.execute(credential.getProjectKey(),
				credential.getEnvironmentKey(), flagKey, request.userKey(), request.variantId());

		return Map.of("data",
				Map.of("flagId", event.getFlagId(), "variantId", event.getVariantId(), "timestamp",
						event.getTimestamp()));
	}
}
