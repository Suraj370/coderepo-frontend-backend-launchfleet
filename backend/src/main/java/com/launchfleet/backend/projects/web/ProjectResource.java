package com.launchfleet.backend.projects.web;

import java.util.LinkedHashMap;
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

import com.launchfleet.backend.projects.Project;
import com.launchfleet.backend.projects.ProjectRepository;
import com.launchfleet.backend.projects.application.CreateProject;
import com.launchfleet.backend.projects.application.ListProjectsForUser;
import com.launchfleet.backend.projects.application.ProjectMembershipView;
import com.launchfleet.backend.projects.application.RenameProject;
import com.launchfleet.backend.shared.ApiException;
import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.ProjectResponseSchema;
import com.launchfleet.backend.users.Role;
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
 * Thin HTTP adapter over the existing Project/ProjectMembership domain (see
 * Project's own Javadoc: it long predates this controller, seeded-only until
 * now). List/rename are scoped to the caller's own memberships - there is
 * deliberately no "list every project in the system" superpower here.
 */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects", description = "Projects the current dashboard user has membership in.")
@SecurityRequirement(name = OpenApiConfig.DASHBOARD_SESSION_SCHEME)
public class ProjectResource {

	private static final int NOT_FOUND = 404;

	private final CreateProject createProject;

	private final ListProjectsForUser listProjectsForUser;

	private final RenameProject renameProject;

	private final ProjectRepository projectRepository;

	public ProjectResource(CreateProject createProject, ListProjectsForUser listProjectsForUser,
			RenameProject renameProject, ProjectRepository projectRepository) {
		this.createProject = createProject;
		this.listProjectsForUser = listProjectsForUser;
		this.renameProject = renameProject;
		this.projectRepository = projectRepository;
	}

	@GetMapping
	@Operation(summary = "List my projects", description = "Only projects the caller has a membership in - not every project in the system.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProjectResponseSchema.class)))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> list(@AuthenticationPrincipal UserPrincipal principal) {
		return Map.of("data", listProjectsForUser.execute(principal.getId()).stream().map(this::toMap).toList());
	}

	@PostMapping
	@Operation(summary = "Create a project", description = "The creator is granted ADMIN membership on the new project in the same operation.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Created.", content = @Content(schema = @Schema(implementation = ProjectResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error (blank key/name).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "A project with this key already exists.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> create(@Valid @RequestBody CreateProjectRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		Project project = createProject.execute(request.key(), request.name(), principal.getId());

		return Map.of("data", toMap(new ProjectMembershipView(project, Role.ADMIN)));
	}

	@PatchMapping("/{projectKey}")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).ADMIN)")
	@Operation(summary = "Rename a project", description = "Requires ADMIN. The project's key is immutable.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Renamed.", content = @Content(schema = @Schema(implementation = ProjectResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error (blank name).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks ADMIN access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> rename(@PathVariable @P("projectKey") String projectKey,
			@Valid @RequestBody RenameProjectRequest request) {
		Project project = resolveProject(projectKey);
		Project renamed = renameProject.execute(project, request.name());

		return Map.of("data", toMap(new ProjectMembershipView(renamed, Role.ADMIN)));
	}

	private Project resolveProject(String projectKey) {
		return projectRepository.findByKey(projectKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "PROJECT_NOT_FOUND", "No project with that key."));
	}

	private Map<String, Object> toMap(ProjectMembershipView view) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", view.project().getId());
		body.put("key", view.project().getKey());
		body.put("name", view.project().getName());
		body.put("role", view.role());
		body.put("createdAt", view.project().getCreatedAt());

		return body;
	}
}
