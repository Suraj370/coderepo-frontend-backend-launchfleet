package com.launchfleet.backend.experiments.adapters.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.launchfleet.backend.experiments.application.CompleteExperiment;
import com.launchfleet.backend.experiments.application.CreateExperiment;
import com.launchfleet.backend.experiments.application.ExperimentVariantMetrics;
import com.launchfleet.backend.experiments.application.GetExperiment;
import com.launchfleet.backend.experiments.application.GetExperimentMetrics;
import com.launchfleet.backend.experiments.application.ListExperiments;
import com.launchfleet.backend.experiments.application.StartExperiment;
import com.launchfleet.backend.experiments.application.UpdateExperiment;
import com.launchfleet.backend.experiments.application.UpdateExperiment.AllocationInput;
import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.domain.Rollout;
import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.ExperimentMetricsResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.ExperimentResponseSchema;
import com.launchfleet.backend.users.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * The dashboard-facing HTTP adapter for experiments (Phase 7) - same style as
 * FeatureFlagResource/ApprovalRequestResource: parses input, authorizes via the
 * existing dashboard RBAC, invokes exactly one use case per endpoint, shapes the
 * JSON response. No business rules live here.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/experiments")
@Tag(name = "Experiments", description = "A/B experiments layered on a feature flag's variants: configuration, lifecycle (DRAFT/RUNNING/COMPLETED/CANCELLED), and per-variant metrics. Assignment/event recording is SDK-facing - see the SDK tag.")
@SecurityRequirement(name = OpenApiConfig.DASHBOARD_SESSION_SCHEME)
public class ExperimentResource {

	private static final String COMMON_404 = "No project/experiment with that key.";

	private final CreateExperiment createExperiment;

	private final ListExperiments listExperiments;

	private final GetExperiment getExperiment;

	private final UpdateExperiment updateExperiment;

	private final StartExperiment startExperiment;

	private final CompleteExperiment completeExperiment;

	private final GetExperimentMetrics getExperimentMetrics;

	public ExperimentResource(CreateExperiment createExperiment, ListExperiments listExperiments,
			GetExperiment getExperiment, UpdateExperiment updateExperiment, StartExperiment startExperiment,
			CompleteExperiment completeExperiment, GetExperimentMetrics getExperimentMetrics) {
		this.createExperiment = createExperiment;
		this.listExperiments = listExperiments;
		this.getExperiment = getExperiment;
		this.updateExperiment = updateExperiment;
		this.startExperiment = startExperiment;
		this.completeExperiment = completeExperiment;
		this.getExperimentMetrics = getExperimentMetrics;
	}

	@PostMapping
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Create an experiment", description = "Requires EDITOR or above. Starts in DRAFT with no allocation/conversion event - configure via PATCH before starting. flagKey must reference an ACTIVE flag; environmentKey must reference an active environment.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Created, status DRAFT.", content = @Content(schema = @Schema(implementation = ExperimentResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project/flag/environment with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "An experiment with this key already exists in this project, the flag is retired, or the environment is retired.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> create(@PathVariable @P("projectKey") String projectKey,
			@Valid @RequestBody CreateExperimentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
		Experiment experiment = createExperiment.execute(projectKey, request.environmentKey(), request.flagKey(),
				request.key(), request.name(), request.description(), principal.getId());

		return Map.of("data", toMap(experiment));
	}

	@GetMapping
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).VIEWER)")
	@Operation(summary = "List experiments", description = "Requires VIEWER or above.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ExperimentResponseSchema.class)))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Lacks VIEWER access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> list(@PathVariable @P("projectKey") String projectKey) {
		return Map.of("data", listExperiments.execute(projectKey).stream().map(this::toMap).toList());
	}

	@GetMapping("/{experimentKey}")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).VIEWER)")
	@Operation(summary = "Get an experiment", description = "Requires VIEWER or above.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ExperimentResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Lacks VIEWER access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = COMMON_404, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> get(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String experimentKey) {
		return Map.of("data", toMap(getExperiment.execute(projectKey, experimentKey)));
	}

	@PatchMapping("/{experimentKey}")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Configure an experiment (DRAFT only)", description = "Requires EDITOR or above. Whole-configuration replacement of name/description/allocation/conversionEventName in one call. Rejected once the experiment has left DRAFT - configuration is frozen for the life of a RUNNING/COMPLETED/CANCELLED experiment. allocation entries must sum to exactly 10000 basis points and every variantId must belong to the referenced flag.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated.", content = @Content(schema = @Schema(implementation = ExperimentResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error, an unknown/duplicate variantId, or allocations not summing to exactly 10000.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = COMMON_404, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "The experiment is not in DRAFT.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> update(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String experimentKey, @Valid @RequestBody UpdateExperimentRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		List<AllocationInput> allocation = request.allocation() == null ? null
				: request.allocation().stream().map(a -> new AllocationInput(a.variantId(), a.percentage())).toList();

		Experiment experiment = updateExperiment.execute(projectKey, experimentKey, request.name(),
				request.description(), allocation, request.conversionEventName(), principal.getId());

		return Map.of("data", toMap(experiment));
	}

	@PostMapping("/{experimentKey}/start")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Start an experiment (DRAFT -> RUNNING)", description = "Requires EDITOR or above. Requires a complete, valid configuration (a non-null allocation and a conversion event name) - from this point on, the allocation is frozen for the life of the experiment and never re-reads the flag's live rollout.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Started, status RUNNING.", content = @Content(schema = @Schema(implementation = ExperimentResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = COMMON_404, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "The experiment is not DRAFT, or is missing an allocation/conversion event name.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> start(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String experimentKey, @AuthenticationPrincipal UserPrincipal principal) {
		return Map.of("data", toMap(startExperiment.execute(projectKey, experimentKey, principal.getId())));
	}

	@PostMapping("/{experimentKey}/stop")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Stop an experiment (RUNNING -> COMPLETED)", description = "Requires EDITOR or above. A deliberate stop, distinct from CANCELLED (which means the experiment became invalid, e.g. via flag/environment retirement, not that it concluded). Terminal.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Stopped, status COMPLETED.", content = @Content(schema = @Schema(implementation = ExperimentResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = COMMON_404, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "The experiment is not RUNNING.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> stop(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String experimentKey, @AuthenticationPrincipal UserPrincipal principal) {
		return Map.of("data", toMap(completeExperiment.execute(projectKey, experimentKey, principal.getId())));
	}

	@GetMapping("/{experimentKey}/metrics")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).VIEWER)")
	@Operation(summary = "Get per-variant experiment metrics", description = "Requires VIEWER or above. One entry per variant in the experiment's frozen allocation: assignedCount, conversionCount (distinct converting users), and conversionRate (0.0 when assignedCount is 0). Empty for a DRAFT experiment with no allocation yet.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ExperimentMetricsResponseSchema.class)))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Lacks VIEWER access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = COMMON_404, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> metrics(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String experimentKey) {
		List<ExperimentVariantMetrics> metrics = getExperimentMetrics.execute(projectKey, experimentKey);

		return Map.of("data", metrics.stream().map(this::toMap).toList());
	}

	private Map<String, Object> toMap(Experiment experiment) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", experiment.getId());
		body.put("projectId", experiment.getProjectId());
		body.put("environmentId", experiment.getEnvironmentId());
		body.put("featureFlagId", experiment.getFeatureFlagId());
		body.put("key", experiment.getKey());
		body.put("name", experiment.getName());
		body.put("description", experiment.getDescription());
		body.put("allocation", experiment.getAllocation() == null ? null : toMap(experiment.getAllocation()));
		body.put("conversionEventName", experiment.getConversionEventName());
		body.put("status", experiment.getStatus());
		body.put("version", experiment.getVersion());
		body.put("createdBy", experiment.getCreatedBy());
		body.put("createdAt", experiment.getCreatedAt());
		body.put("updatedBy", experiment.getUpdatedBy());
		body.put("updatedAt", experiment.getUpdatedAt());

		return body;
	}

	private Map<String, Object> toMap(Rollout allocation) {
		return Map.of("allocations", allocation.sortedByVariantId().stream().map(this::toMap).toList());
	}

	private Map<String, Object> toMap(Allocation allocation) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("variantId", allocation.variantId());
		body.put("percentage", allocation.percentage());

		return body;
	}

	private Map<String, Object> toMap(ExperimentVariantMetrics metrics) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("variantId", metrics.variantId());
		body.put("assignedCount", metrics.assignedCount());
		body.put("conversionCount", metrics.conversionCount());
		body.put("conversionRate", metrics.conversionRate());

		return body;
	}
}
