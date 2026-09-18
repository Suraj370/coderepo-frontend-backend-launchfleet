package com.launchfleet.backend.experiments.adapters.web.sdk;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.launchfleet.backend.experiments.application.CreateExperimentAssignment;
import com.launchfleet.backend.experiments.application.RecordExperimentEvent;
import com.launchfleet.backend.experiments.domain.ExperimentAssignment;
import com.launchfleet.backend.experiments.domain.ExperimentEvent;
import com.launchfleet.backend.sdk.SdkCredential;
import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.ExperimentAssignmentResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.ExperimentEventResponseSchema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * The SDK-facing HTTP adapter for experiment assignments/events (locked decision
 * 8): lives entirely under the existing sdkFilterChain (SecurityConstants.
 * SDK_MATCHER = "/api/v1/sdk/**"), which already requires SDK_SERVER for every
 * request on this matcher by default - no new SecurityFilterChain, no
 * @PreAuthorize, no change to SdkCredentialAuthenticationFilter/
 * SdkAuthenticationToken. Exactly mirrors SdkConfigurationResource's own reasoning
 * (see its Javadoc): that chain is stateless and header-only, so a dashboard
 * session can never reach this route either.
 *
 * Project/environment scope comes ONLY from the authenticated SdkCredential's own
 * projectKey/environmentKey - never from a path, query, or body parameter a caller
 * could tamper with to reach a different project/environment's experiment (locked
 * decision 8's explicit requirement). The path only ever carries the experiment
 * key, which is looked up scoped to the credential's own project, and then
 * cross-checked against the credential's own environment (see
 * CreateExperimentAssignment/RecordExperimentEvent).
 *
 * No SDK jar changes are involved here at all (locked decision 13) - this endpoint
 * is called directly over HTTP by the customer's own application code, the same
 * way any other SDK-credential-authenticated call would be, just not through the
 * launchfleet-sdk artifact.
 */
@RestController
@RequestMapping("/api/v1/sdk/experiments")
@Tag(name = "SDK", description = "Endpoints called by SDK_SERVER-authenticated customer application code (not the dashboard, not necessarily via the launchfleet-sdk Java artifact): configuration distribution and experiment assignment/event recording. Project/environment scope always comes from the authenticated credential, never from a request parameter.")
@SecurityRequirement(name = OpenApiConfig.SDK_SERVER_SCHEME)
public class ExperimentEventResource {

	private static final String COMMON_404 = "No experiment with that key in the credential's project/environment.";

	private final CreateExperimentAssignment createExperimentAssignment;

	private final RecordExperimentEvent recordExperimentEvent;

	public ExperimentEventResource(CreateExperimentAssignment createExperimentAssignment,
			RecordExperimentEvent recordExperimentEvent) {
		this.createExperimentAssignment = createExperimentAssignment;
		this.recordExperimentEvent = recordExperimentEvent;
	}

	@PostMapping("/{experimentKey}/assignments")
	@Operation(summary = "Get or create this user's experiment assignment", description = "Sticky: if an assignment already exists for (experiment, userKey) it is returned unchanged, regardless of any later configuration change. Otherwise, evaluates the flag's current targeting/segments/enabled state for eligibility; an eligible user is assigned a variant from the experiment's frozen allocation (deterministic hashing, never random) and the assignment is persisted (first assignment wins under concurrent requests for the same user).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The user's assignment (existing or newly created).", content = @Content(schema = @Schema(implementation = ExperimentAssignmentResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error (blank userKey).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "Missing/invalid/expired/inactive SDK_SERVER credential.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "A valid credential that is not SDK_SERVER (e.g. SDK_CLIENT_SIDE).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = COMMON_404, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "The experiment is not RUNNING, its flag is retired/disabled, its environment is retired, or the user does not match the flag's current targeting (no new assignment is created in any of these cases).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> createAssignment(@AuthenticationPrincipal SdkCredential credential,
			@PathVariable String experimentKey, @Valid @RequestBody CreateExperimentAssignmentRequest request) {
		ExperimentAssignment assignment = createExperimentAssignment.execute(credential.getProjectKey(),
				credential.getEnvironmentKey(), experimentKey, request.userKey());

		return Map.of("data", toMap(assignment));
	}

	@PostMapping("/{experimentKey}/events")
	@Operation(summary = "Record a conversion event", description = "The event's variant is always the caller's persisted ExperimentAssignment's variant - the request body has no variant field at all, so a client can never override the authoritative assignment. Requires an assignment to already exist for (experiment, userKey); it is never created implicitly here.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Recorded.", content = @Content(schema = @Schema(implementation = ExperimentEventResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error (blank userKey/eventName).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "Missing/invalid/expired/inactive SDK_SERVER credential.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "A valid credential that is not SDK_SERVER.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = COMMON_404 + " Also returned when no assignment exists yet for this (experiment, userKey) - establish one via POST .../assignments first.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "The experiment is not RUNNING.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> recordEvent(@AuthenticationPrincipal SdkCredential credential,
			@PathVariable String experimentKey, @Valid @RequestBody RecordExperimentEventRequest request) {
		ExperimentEvent event = recordExperimentEvent.execute(credential.getProjectKey(),
				credential.getEnvironmentKey(), experimentKey, request.userKey(), request.eventName());

		return Map.of("data", toMap(event));
	}

	private Map<String, Object> toMap(ExperimentAssignment assignment) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("experimentId", assignment.getExperimentId());
		body.put("userKey", assignment.getUserKey());
		body.put("variantId", assignment.getVariantId());
		body.put("assignedAt", assignment.getAssignedAt());

		return body;
	}

	private Map<String, Object> toMap(ExperimentEvent event) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("experimentId", event.getExperimentId());
		body.put("userKey", event.getUserKey());
		body.put("eventName", event.getEventName());
		body.put("variantId", event.getVariantId());
		body.put("timestamp", event.getTimestamp());

		return body;
	}
}
