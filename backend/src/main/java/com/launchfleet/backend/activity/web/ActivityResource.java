package com.launchfleet.backend.activity.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.launchfleet.backend.activity.ActivityLog;
import com.launchfleet.backend.activity.ActivityLogRepository;
import com.launchfleet.backend.projects.Project;
import com.launchfleet.backend.projects.ProjectRepository;
import com.launchfleet.backend.shared.ApiException;
import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.ActivityLogResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The last 50 notable actions in this project - see ActivityLogService for what gets recorded. */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/activity")
@Tag(name = "Activity", description = "A recent-activity feed for this project.")
@SecurityRequirement(name = OpenApiConfig.DASHBOARD_SESSION_SCHEME)
public class ActivityResource {

	private static final int NOT_FOUND = 404;

	private final ActivityLogRepository activityLogRepository;

	private final ProjectRepository projectRepository;

	public ActivityResource(ActivityLogRepository activityLogRepository, ProjectRepository projectRepository) {
		this.activityLogRepository = activityLogRepository;
		this.projectRepository = projectRepository;
	}

	@GetMapping
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).VIEWER)")
	@Operation(summary = "List recent activity", description = "Requires VIEWER or above. The 50 most recent entries, newest first.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ActivityLogResponseSchema.class)))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Lacks VIEWER access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> list(@PathVariable @P("projectKey") String projectKey) {
		Project project = projectRepository.findByKey(projectKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "PROJECT_NOT_FOUND", "No project with that key."));

		return Map.of("data", activityLogRepository.findTop50ByProjectIdOrderByOccurredAtDesc(project.getId()).stream()
				.map(this::toMap).toList());
	}

	private Map<String, Object> toMap(ActivityLog entry) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", entry.getId());
		body.put("actorUserId", entry.getActorUserId());
		body.put("actorName", entry.getActorName());
		body.put("action", entry.getAction());
		body.put("subjectType", entry.getSubjectType());
		body.put("subjectKey", entry.getSubjectKey());
		body.put("environmentId", entry.getEnvironmentId());
		body.put("occurredAt", entry.getOccurredAt());

		return body;
	}
}
