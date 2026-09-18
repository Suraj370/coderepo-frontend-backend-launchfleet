package com.launchfleet.backend.projects.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.launchfleet.backend.projects.Project;
import com.launchfleet.backend.projects.ProjectMembershipRepository;
import com.launchfleet.backend.projects.ProjectRepository;
import com.launchfleet.backend.projects.application.AddProjectMember;
import com.launchfleet.backend.projects.application.ProjectMemberView;
import com.launchfleet.backend.projects.application.RemoveProjectMember;
import com.launchfleet.backend.projects.application.UpdateProjectMemberRole;
import com.launchfleet.backend.shared.ApiException;
import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.ProjectMembershipResponseSchema;
import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserRepository;

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
 * Per-project team membership. ProjectMembership is the sole source of
 * dashboard authorization (see ProjectAccessService) - there is no separate
 * global role on User.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/members")
@Tag(name = "Project Members", description = "Per-project team membership (userId + Role).")
@SecurityRequirement(name = OpenApiConfig.DASHBOARD_SESSION_SCHEME)
public class ProjectMembershipResource {

	private static final int NOT_FOUND = 404;

	private final AddProjectMember addProjectMember;

	private final UpdateProjectMemberRole updateProjectMemberRole;

	private final RemoveProjectMember removeProjectMember;

	private final ProjectRepository projectRepository;

	private final ProjectMembershipRepository projectMembershipRepository;

	private final UserRepository userRepository;

	public ProjectMembershipResource(AddProjectMember addProjectMember,
			UpdateProjectMemberRole updateProjectMemberRole, RemoveProjectMember removeProjectMember,
			ProjectRepository projectRepository, ProjectMembershipRepository projectMembershipRepository,
			UserRepository userRepository) {
		this.addProjectMember = addProjectMember;
		this.updateProjectMemberRole = updateProjectMemberRole;
		this.removeProjectMember = removeProjectMember;
		this.projectRepository = projectRepository;
		this.projectMembershipRepository = projectMembershipRepository;
		this.userRepository = userRepository;
	}

	@GetMapping
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).VIEWER)")
	@Operation(summary = "List this project's team", description = "Requires VIEWER or above.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProjectMembershipResponseSchema.class)))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Lacks VIEWER access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> list(@PathVariable @P("projectKey") String projectKey) {
		resolveProject(projectKey);

		List<Map<String, Object>> members = projectMembershipRepository.findByProjectKey(projectKey).stream()
				.map(membership -> {
					User user = userRepository.findById(membership.getUserId()).orElse(null);
					return toMap(new ProjectMemberView(membership, user != null ? user.getName() : "Unknown user",
							user != null ? user.getEmail() : ""));
				})
				.toList();

		return Map.of("data", members);
	}

	@PostMapping
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).ADMIN)")
	@Operation(summary = "Add a team member", description = "Requires ADMIN. The email must belong to an existing, active, registered user - there is no email-invite flow.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Added.", content = @Content(schema = @Schema(implementation = ProjectMembershipResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks ADMIN access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key, or no active user with that email.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "This user already has access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> add(@PathVariable @P("projectKey") String projectKey,
			@Valid @RequestBody AddProjectMemberRequest request) {
		resolveProject(projectKey);

		return Map.of("data", toMap(addProjectMember.execute(projectKey, request.email(), request.role())));
	}

	@PatchMapping("/{membershipId}")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).ADMIN)")
	@Operation(summary = "Change a team member's role", description = "Requires ADMIN. Rejected if it would leave the project with zero ADMINs.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated.", content = @Content(schema = @Schema(implementation = ProjectMembershipResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks ADMIN access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project or membership with that id.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "This change would leave the project with zero ADMINs.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> updateRole(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String membershipId, @Valid @RequestBody UpdateProjectMemberRoleRequest request) {
		resolveProject(projectKey);

		return Map.of("data", toMap(updateProjectMemberRole.execute(projectKey, membershipId, request.role())));
	}

	@DeleteMapping("/{membershipId}")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).ADMIN)")
	@Operation(summary = "Remove a team member", description = "Requires ADMIN. Rejected if it would leave the project with zero ADMINs.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Removed."),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks ADMIN access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project or membership with that id.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "This would leave the project with zero ADMINs.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> remove(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String membershipId) {
		resolveProject(projectKey);
		removeProjectMember.execute(projectKey, membershipId);

		return Map.of("data", Map.of("removed", true));
	}

	private Project resolveProject(String projectKey) {
		return projectRepository.findByKey(projectKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "PROJECT_NOT_FOUND", "No project with that key."));
	}

	private Map<String, Object> toMap(ProjectMemberView view) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", view.membership().getId());
		body.put("userId", view.membership().getUserId());
		body.put("name", view.userName());
		body.put("email", view.userEmail());
		body.put("role", view.membership().getRole());
		body.put("createdAt", view.membership().getCreatedAt());

		return body;
	}
}
