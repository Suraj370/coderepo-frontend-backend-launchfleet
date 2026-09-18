package com.launchfleet.backend.approvals.adapters.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.launchfleet.backend.approvals.application.ApproveApprovalRequest;
import com.launchfleet.backend.approvals.application.CancelApprovalRequest;
import com.launchfleet.backend.approvals.application.GetApprovalRequest;
import com.launchfleet.backend.approvals.application.ListApprovalRequests;
import com.launchfleet.backend.approvals.application.RejectApprovalRequest;
import com.launchfleet.backend.approvals.application.ScheduleApprovalRequest;
import com.launchfleet.backend.approvals.application.SubmitApprovalRequest;
import com.launchfleet.backend.approvals.application.SubmitApprovalRequest.AllocationInput;
import com.launchfleet.backend.approvals.application.SubmitApprovalRequest.TargetingRuleInput;
import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.featureflags.adapters.web.TargetingRuleRequest;
import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.Rollout;
import com.launchfleet.backend.featureflags.domain.TargetingRule;
import com.launchfleet.backend.projects.ProjectAccessService;
import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.ApprovalRequestResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;
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
 * The HTTP adapter for the approval workflow (Phase 6, locked architecture rule 21).
 * Same style as FeatureFlagResource: parses input, authorizes via the existing
 * dashboard RBAC, invokes exactly one use case per endpoint, shapes the JSON
 * response. RBAC here is two layers, matching CancelApprovalRequest's Javadoc: role-
 * only checks (VIEWER/EDITOR/ADMIN) are @PreAuthorize; ownership/ADMIN-only-for-
 * SCHEDULED checks that depend on data, not just role, live in the use case.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}")
@Tag(name = "Approvals", description = "The two-person approval workflow for a proposed FeatureFlagConfig change: submit, review (approve/reject), schedule for later execution, or cancel.")
@SecurityRequirement(name = OpenApiConfig.DASHBOARD_SESSION_SCHEME)
public class ApprovalRequestResource {

	private static final String STATUS_CONFLICT = "The request is not in a status this action can be performed on (see ApprovalStatus's lifecycle).";

	private final SubmitApprovalRequest submitApprovalRequest;

	private final ListApprovalRequests listApprovalRequests;

	private final GetApprovalRequest getApprovalRequest;

	private final ApproveApprovalRequest approveApprovalRequest;

	private final RejectApprovalRequest rejectApprovalRequest;

	private final ScheduleApprovalRequest scheduleApprovalRequest;

	private final CancelApprovalRequest cancelApprovalRequest;

	private final ProjectAccessService projectAccess;

	public ApprovalRequestResource(SubmitApprovalRequest submitApprovalRequest,
			ListApprovalRequests listApprovalRequests, GetApprovalRequest getApprovalRequest,
			ApproveApprovalRequest approveApprovalRequest, RejectApprovalRequest rejectApprovalRequest,
			ScheduleApprovalRequest scheduleApprovalRequest, CancelApprovalRequest cancelApprovalRequest,
			ProjectAccessService projectAccess) {
		this.submitApprovalRequest = submitApprovalRequest;
		this.listApprovalRequests = listApprovalRequests;
		this.getApprovalRequest = getApprovalRequest;
		this.approveApprovalRequest = approveApprovalRequest;
		this.rejectApprovalRequest = rejectApprovalRequest;
		this.scheduleApprovalRequest = scheduleApprovalRequest;
		this.cancelApprovalRequest = cancelApprovalRequest;
		this.projectAccess = projectAccess;
	}

	@PostMapping("/flags/{flagKey}/environments/{environmentKey}/approval-requests")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Submit an approval request", description = "Requires EDITOR or above. Captures the current FeatureFlagConfig version as baseConfigVersion (staleness protection - see ApproveApprovalRequest) and stores the complete proposed configuration immutably; targetingRules/rollout reuse the same request shapes as the direct dashboard editing endpoints.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Submitted, status PENDING.", content = @Content(schema = @Schema(implementation = ApprovalRequestResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error, or a proposed defaultVariantId/rollout variantId that does not belong to this flag.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project/flag/environment with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> submit(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String flagKey, @PathVariable String environmentKey,
			@Valid @RequestBody SubmitApprovalRequestRequest request, @AuthenticationPrincipal UserPrincipal principal) {
		List<TargetingRuleInput> targetingRules = request.targetingRules() == null ? List.of()
				: request.targetingRules().stream().map(this::toInput).toList();
		List<AllocationInput> allocations = request.rollout() == null ? List.of()
				: request.rollout().allocations().stream()
						.map(a -> new AllocationInput(a.variantId(), a.percentage())).toList();

		ApprovalRequest saved = submitApprovalRequest.execute(projectKey, flagKey, environmentKey, request.enabled(),
				request.defaultVariantId(), targetingRules, allocations, principal.getId());

		return Map.of("data", toMap(saved));
	}

	@GetMapping("/approval-requests")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).VIEWER)")
	@Operation(summary = "List approval requests", description = "Requires VIEWER or above.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApprovalRequestResponseSchema.class)))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Lacks VIEWER access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> list(@PathVariable @P("projectKey") String projectKey) {
		return Map.of("data", listApprovalRequests.execute(projectKey).stream().map(this::toMap).toList());
	}

	@GetMapping("/approval-requests/{requestId}")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).VIEWER)")
	@Operation(summary = "Get an approval request", description = "Requires VIEWER or above.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ApprovalRequestResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Lacks VIEWER access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project/approval request with that key/id.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> get(@PathVariable @P("projectKey") String projectKey, @PathVariable String requestId) {
		return Map.of("data", toMap(getApprovalRequest.execute(projectKey, requestId)));
	}

	@PostMapping("/approval-requests/{requestId}/approve")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).ADMIN)")
	@Operation(summary = "Approve an approval request", description = "Requires ADMIN. Only a PENDING request whose baseConfigVersion still matches the flag's current FeatureFlagConfig version can be approved (staleness protection) - immediately applies the proposed configuration. approvalComment is optional.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Approved and applied, status APPLIED.", content = @Content(schema = @Schema(implementation = ApprovalRequestResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or caller is not an ADMIN in this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project/approval request with that key/id.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = STATUS_CONFLICT + " Also returned when the underlying FeatureFlagConfig changed since baseConfigVersion was captured (stale proposal).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> approve(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String requestId, @RequestBody(required = false) ApproveApprovalRequestRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		String comment = request == null ? null : request.approvalComment();

		return Map.of("data",
				toMap(approveApprovalRequest.execute(projectKey, requestId, principal.getId(), comment)));
	}

	@PostMapping("/approval-requests/{requestId}/reject")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).ADMIN)")
	@Operation(summary = "Reject an approval request", description = "Requires ADMIN. Only a PENDING request can be rejected. rejectionComment is mandatory.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Rejected, status REJECTED.", content = @Content(schema = @Schema(implementation = ApprovalRequestResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error (blank rejectionComment).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or caller is not an ADMIN in this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project/approval request with that key/id.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = STATUS_CONFLICT, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> reject(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String requestId, @Valid @RequestBody RejectApprovalRequestRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return Map.of("data", toMap(
				rejectApprovalRequest.execute(projectKey, requestId, principal.getId(), request.rejectionComment())));
	}

	@PostMapping("/approval-requests/{requestId}/schedule")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).ADMIN)")
	@Operation(summary = "Schedule an approval request for later execution", description = "Requires ADMIN. Only a PENDING request whose baseConfigVersion still matches can be scheduled. scheduledAt must be strictly in the future; a background scheduler applies the proposed configuration (inside a real MongoDB multi-document transaction, together with the request's own status write) once it is due.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Scheduled, status SCHEDULED.", content = @Content(schema = @Schema(implementation = ApprovalRequestResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error, or scheduledAt is not strictly in the future.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or caller is not an ADMIN in this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project/approval request with that key/id.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = STATUS_CONFLICT + " Also returned for a stale proposal.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> schedule(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String requestId, @Valid @RequestBody ScheduleApprovalRequestRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return Map.of("data", toMap(scheduleApprovalRequest.execute(projectKey, requestId, principal.getId(),
				request.approvalComment(), request.scheduledAt())));
	}

	/**
	 * EDITOR is the minimum role to reach this at all (a VIEWER can never cancel
	 * anything); whether THIS caller may cancel THIS request in ITS current status is
	 * a data-dependent decision made inside CancelApprovalRequest (see its Javadoc),
	 * which is why actingUserIsAdmin is resolved here and passed in rather than
	 * expressed as a second @PreAuthorize clause.
	 */
	@PostMapping("/approval-requests/{requestId}/cancel")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Cancel an approval request", description = "Requires EDITOR or above to reach this endpoint at all, but that is not sufficient by itself: a PENDING request may only be cancelled by its own submitter or an ADMIN; a SCHEDULED request may only be cancelled by an ADMIN. This data-dependent check happens inside the use case, not as a second @PreAuthorize, since it depends on who submitted the request and its current status, not just the caller's role.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Cancelled, status CANCELLED.", content = @Content(schema = @Schema(implementation = ApprovalRequestResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token; lacks EDITOR access to this project; or (data-dependent) not the submitter and not an ADMIN.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project/approval request with that key/id.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = STATUS_CONFLICT, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> cancel(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String requestId, @AuthenticationPrincipal UserPrincipal principal,
			Authentication authentication) {
		boolean isAdmin = projectAccess.hasRole(authentication, projectKey, Role.ADMIN);

		return Map.of("data",
				toMap(cancelApprovalRequest.execute(projectKey, requestId, principal.getId(), isAdmin)));
	}

	private TargetingRuleInput toInput(TargetingRuleRequest request) {
		List<Condition> conditions = request.conditions().stream()
				.map(c -> new Condition(c.type(), c.attribute(), c.operator(), c.values())).toList();

		return new TargetingRuleInput(request.priority(), conditions, request.variantId());
	}

	private Map<String, Object> toMap(ApprovalRequest request) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", request.getId());
		body.put("projectId", request.getProjectId());
		body.put("featureFlagId", request.getFeatureFlagId());
		body.put("environmentId", request.getEnvironmentId());
		body.put("status", request.getStatus());
		body.put("baseConfigVersion", request.getBaseConfigVersion());
		body.put("proposedConfig", toMap(request.getProposedConfig()));
		body.put("submittedBy", request.getSubmittedBy());
		body.put("submittedAt", request.getSubmittedAt());
		body.put("reviewedBy", request.getReviewedBy());
		body.put("reviewedAt", request.getReviewedAt());
		body.put("approvalComment", request.getApprovalComment());
		body.put("rejectionComment", request.getRejectionComment());
		body.put("scheduledAt", request.getScheduledAt());
		body.put("appliedVersion", request.getAppliedVersion());
		body.put("cancellationReason", request.getCancellationReason());
		body.put("cancelledBy", request.getCancelledBy());
		body.put("cancelledAt", request.getCancelledAt());

		return body;
	}

	private Map<String, Object> toMap(com.launchfleet.backend.approvals.domain.ProposedConfig proposedConfig) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("enabled", proposedConfig.enabled());
		body.put("defaultVariantId", proposedConfig.defaultVariantId());
		body.put("targetingRules", proposedConfig.targetingRules().stream().map(this::toMap).toList());
		body.put("rollout", proposedConfig.rollout() == null ? null : toMap(proposedConfig.rollout()));

		return body;
	}

	private Map<String, Object> toMap(TargetingRule rule) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", rule.getId());
		body.put("priority", rule.getPriority());
		body.put("conditions", rule.getConditions().stream().map(this::toMap).toList());
		body.put("variantId", rule.getVariantId());

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

	private Map<String, Object> toMap(Rollout rollout) {
		return Map.of("allocations", rollout.sortedByVariantId().stream().map(this::toMap).toList());
	}

	private Map<String, Object> toMap(Allocation allocation) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("variantId", allocation.variantId());
		body.put("percentage", allocation.percentage());

		return body;
	}
}
