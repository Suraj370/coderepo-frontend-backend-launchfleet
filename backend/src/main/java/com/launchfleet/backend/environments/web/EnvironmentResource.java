package com.launchfleet.backend.environments.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.launchfleet.backend.environments.Environment;
import com.launchfleet.backend.environments.EnvironmentRepository;
import com.launchfleet.backend.environments.application.CreateEnvironment;
import com.launchfleet.backend.environments.application.ListEnvironments;
import com.launchfleet.backend.environments.application.RetireEnvironment;
import com.launchfleet.backend.projects.Project;
import com.launchfleet.backend.projects.ProjectRepository;
import com.launchfleet.backend.shared.ApiException;
import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.EnvironmentResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;
import com.launchfleet.backend.users.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Thin HTTP adapter: parses input, authorizes via the existing dashboard RBAC
 * (unchanged), invokes one application use case, shapes the JSON response. No
 * environment business rules live here.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/environments")
@Tag(name = "Environments", description = "Per-project environments (e.g. production/staging). Every flag has one FeatureFlagConfig per environment.")
@SecurityRequirement(name = OpenApiConfig.DASHBOARD_SESSION_SCHEME)
public class EnvironmentResource {

	private static final int NOT_FOUND = 404;

	private final CreateEnvironment createEnvironment;

	private final ListEnvironments listEnvironments;

	private final RetireEnvironment retireEnvironment;

	private final ProjectRepository projectRepository;

	private final EnvironmentRepository environmentRepository;

	public EnvironmentResource(CreateEnvironment createEnvironment, ListEnvironments listEnvironments,
			RetireEnvironment retireEnvironment, ProjectRepository projectRepository,
			EnvironmentRepository environmentRepository) {
		this.createEnvironment = createEnvironment;
		this.listEnvironments = listEnvironments;
		this.retireEnvironment = retireEnvironment;
		this.projectRepository = projectRepository;
		this.environmentRepository = environmentRepository;
	}

	@GetMapping
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).VIEWER)")
	@Operation(summary = "List environments", description = "Requires VIEWER or above in this project.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = EnvironmentResponseSchema.class)))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Authenticated but lacks VIEWER access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> list(@PathVariable @P("projectKey") String projectKey) {
		Project project = resolveProject(projectKey);

		return Map.of("data", listEnvironments.execute(project.getId()).stream().map(this::toMap).toList());
	}

	@PostMapping
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Create an environment", description = "Requires EDITOR or above in this project. The environment starts ACTIVE.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Created.", content = @Content(schema = @Schema(implementation = EnvironmentResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error (blank key/name).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "An environment with this key already exists in this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> create(@PathVariable @P("projectKey") String projectKey,
			@Valid @RequestBody CreateEnvironmentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
		Project project = resolveProject(projectKey);
		Environment environment = createEnvironment.execute(project.getId(), request.key(), request.name(),
				principal.getId());

		return Map.of("data", toMap(environment));
	}

	@PostMapping("/{environmentKey}/retire")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Retire an environment", description = "Requires EDITOR or above. Terminal - an already-retired environment cannot be retired again. Cascades to cancel any active approval requests/experiments scoped to this environment.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Retired.", content = @Content(schema = @Schema(implementation = EnvironmentResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project or environment with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "The environment is already retired.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> retire(@PathVariable @P("projectKey") String projectKey,
			@Parameter(description = "The environment's project-scoped key.") @PathVariable String environmentKey,
			@AuthenticationPrincipal UserPrincipal principal) {
		Project project = resolveProject(projectKey);
		Environment environment = environmentRepository.findByProjectIdAndKey(project.getId(), environmentKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "ENVIRONMENT_NOT_FOUND",
						"No environment '" + environmentKey + "' in this project."));

		return Map.of("data", toMap(retireEnvironment.execute(environment, principal.getId())));
	}

	private Project resolveProject(String projectKey) {
		return projectRepository.findByKey(projectKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "PROJECT_NOT_FOUND", "No project with that key."));
	}

	private Map<String, Object> toMap(Environment environment) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", environment.getId());
		body.put("projectId", environment.getProjectId());
		body.put("key", environment.getKey());
		body.put("name", environment.getName());
		body.put("status", environment.getStatus());
		body.put("createdAt", environment.getCreatedAt());

		return body;
	}
}
