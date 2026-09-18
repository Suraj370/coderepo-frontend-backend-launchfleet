package com.launchfleet.backend.featureflags.adapters.web;

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

import com.launchfleet.backend.featureflags.application.CreateSegment;
import com.launchfleet.backend.featureflags.application.GetSegment;
import com.launchfleet.backend.featureflags.application.ListSegments;
import com.launchfleet.backend.featureflags.application.RetireSegment;
import com.launchfleet.backend.featureflags.application.UpdateSegment;
import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.SegmentResponseSchema;
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
 * The HTTP adapter for Segment CRUD/lifecycle: parses input, authorizes via the
 * existing dashboard RBAC (unchanged), invokes exactly one use case per endpoint,
 * and shapes the JSON response. No business rules live here.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/segments")
@Tag(name = "Segments", description = "Reusable, project-scoped (not environment-scoped) named user groups, referenced by SEGMENT_MATCH targeting-rule conditions.")
@SecurityRequirement(name = OpenApiConfig.DASHBOARD_SESSION_SCHEME)
public class SegmentResource {

	private final CreateSegment createSegment;

	private final ListSegments listSegments;

	private final GetSegment getSegment;

	private final UpdateSegment updateSegment;

	private final RetireSegment retireSegment;

	public SegmentResource(CreateSegment createSegment, ListSegments listSegments, GetSegment getSegment,
			UpdateSegment updateSegment, RetireSegment retireSegment) {
		this.createSegment = createSegment;
		this.listSegments = listSegments;
		this.getSegment = getSegment;
		this.updateSegment = updateSegment;
		this.retireSegment = retireSegment;
	}

	@PostMapping
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Create a segment", description = "Requires EDITOR or above. Conditions must be ATTRIBUTE or USER_KEY only - a segment cannot reference another segment (SEGMENT_MATCH is rejected), which prevents reference cycles by construction.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Created.", content = @Content(schema = @Schema(implementation = SegmentResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error, or a SEGMENT_MATCH condition (not allowed within a segment).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "A segment with this key already exists in this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> create(@PathVariable @P("projectKey") String projectKey,
			@Valid @RequestBody CreateSegmentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
		List<Condition> conditions = toConditions(request.conditions());

		return Map.of("data", toMap(
				createSegment.execute(projectKey, request.key(), request.name(), conditions, principal.getId())));
	}

	@GetMapping
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).VIEWER)")
	@Operation(summary = "List segments", description = "Requires VIEWER or above.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SegmentResponseSchema.class)))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Lacks VIEWER access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> list(@PathVariable @P("projectKey") String projectKey) {
		return Map.of("data", listSegments.execute(projectKey).stream().map(this::toMap).toList());
	}

	@GetMapping("/{segmentKey}")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).VIEWER)")
	@Operation(summary = "Get a segment", description = "Requires VIEWER or above.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SegmentResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Lacks VIEWER access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project/segment with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> get(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String segmentKey) {
		return Map.of("data", toMap(getSegment.execute(projectKey, segmentKey)));
	}

	@PatchMapping("/{segmentKey}")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Update a segment's name/conditions", description = "Requires EDITOR or above. Whole-conditions-list replacement. Existing targeting rules that reference this segment are unaffected by a name/condition change.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated.", content = @Content(schema = @Schema(implementation = SegmentResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error, or a SEGMENT_MATCH condition.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project/segment with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> update(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String segmentKey, @Valid @RequestBody UpdateSegmentRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		List<Condition> conditions = toConditions(request.conditions());

		return Map.of("data", toMap(
				updateSegment.execute(projectKey, segmentKey, request.name(), conditions, principal.getId())));
	}

	@PostMapping("/{segmentKey}/retire")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Retire a segment", description = "Requires EDITOR or above. Terminal. Whether a retired segment still referenced by an active targeting rule is a problem is not enforced here - see RetireSegment.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Retired.", content = @Content(schema = @Schema(implementation = SegmentResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project/segment with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "The segment is already retired.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> retire(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String segmentKey, @AuthenticationPrincipal UserPrincipal principal) {
		return Map.of("data", toMap(retireSegment.execute(projectKey, segmentKey, principal.getId())));
	}

	static List<Condition> toConditions(List<ConditionRequest> requests) {
		return requests.stream()
				.map(r -> new Condition(r.type(), r.attribute(), r.operator(), r.values()))
				.toList();
	}

	private Map<String, Object> toMap(Segment segment) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", segment.getId());
		body.put("projectId", segment.getProjectId());
		body.put("key", segment.getKey());
		body.put("name", segment.getName());
		body.put("status", segment.getStatus());
		body.put("conditions", segment.getConditions().stream().map(this::toMap).toList());
		body.put("createdBy", segment.getCreatedBy());
		body.put("createdAt", segment.getCreatedAt());
		body.put("updatedBy", segment.getUpdatedBy());
		body.put("updatedAt", segment.getUpdatedAt());

		return body;
	}

	private Map<String, Object> toMap(Condition condition) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", condition.type());
		body.put("attribute", condition.attribute());
		body.put("operator", condition.operator());
		body.put("values", condition.values());

		return body;
	}
}
