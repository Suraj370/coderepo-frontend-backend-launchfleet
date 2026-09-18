package com.launchfleet.backend.featureflags.adapters.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.launchfleet.backend.featureflags.application.AddTargetingRule;
import com.launchfleet.backend.featureflags.application.CreateFeatureFlag;
import com.launchfleet.backend.featureflags.application.FeatureFlagView;
import com.launchfleet.backend.featureflags.application.GetFeatureFlag;
import com.launchfleet.backend.featureflags.application.ListFeatureFlags;
import com.launchfleet.backend.featureflags.application.RemoveRollout;
import com.launchfleet.backend.featureflags.application.RemoveTargetingRule;
import com.launchfleet.backend.featureflags.application.RetireFeatureFlag;
import com.launchfleet.backend.featureflags.application.SetRollout;
import com.launchfleet.backend.featureflags.application.SetRollout.AllocationInput;
import com.launchfleet.backend.featureflags.application.UpdateEnvironmentConfig;
import com.launchfleet.backend.featureflags.application.UpdateFeatureFlag;
import com.launchfleet.backend.featureflags.application.UpdateTargetingRule;
import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.Rollout;
import com.launchfleet.backend.featureflags.domain.TargetingRule;
import com.launchfleet.backend.featureflags.domain.Variant;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.FeatureFlagResponseSchema;
import com.launchfleet.backend.users.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * The HTTP adapter: parses input, authorizes via the existing dashboard RBAC
 * (unchanged - see SecurityConfig/ProjectAccessService), invokes exactly one use
 * case per endpoint, and shapes the JSON response. No feature-flag business rules
 * live here - those are in the domain and application layers. SDK credentials never
 * reach this resource: it's entirely under the dashboard chain's /api/v1/** matcher.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/flags")
@Tag(name = "Feature Flags", description = "Feature flag CRUD/lifecycle and their per-environment configuration.")
@SecurityRequirement(name = OpenApiConfig.DASHBOARD_SESSION_SCHEME)
public class FeatureFlagResource {

	private static final String COMMON_404 = "No project/flag (or environment/targeting rule, where applicable) with that key/id.";

	private final CreateFeatureFlag createFeatureFlag;

	private final ListFeatureFlags listFeatureFlags;

	private final GetFeatureFlag getFeatureFlag;

	private final UpdateFeatureFlag updateFeatureFlag;

	private final UpdateEnvironmentConfig updateEnvironmentConfig;

	private final RetireFeatureFlag retireFeatureFlag;

	private final AddTargetingRule addTargetingRule;

	private final UpdateTargetingRule updateTargetingRule;

	private final RemoveTargetingRule removeTargetingRule;

	private final SetRollout setRollout;

	private final RemoveRollout removeRollout;

	public FeatureFlagResource(CreateFeatureFlag createFeatureFlag, ListFeatureFlags listFeatureFlags,
			GetFeatureFlag getFeatureFlag, UpdateFeatureFlag updateFeatureFlag,
			UpdateEnvironmentConfig updateEnvironmentConfig, RetireFeatureFlag retireFeatureFlag,
			AddTargetingRule addTargetingRule, UpdateTargetingRule updateTargetingRule,
			RemoveTargetingRule removeTargetingRule, SetRollout setRollout, RemoveRollout removeRollout) {
		this.createFeatureFlag = createFeatureFlag;
		this.listFeatureFlags = listFeatureFlags;
		this.getFeatureFlag = getFeatureFlag;
		this.updateFeatureFlag = updateFeatureFlag;
		this.updateEnvironmentConfig = updateEnvironmentConfig;
		this.retireFeatureFlag = retireFeatureFlag;
		this.addTargetingRule = addTargetingRule;
		this.updateTargetingRule = updateTargetingRule;
		this.removeTargetingRule = removeTargetingRule;
		this.setRollout = setRollout;
		this.removeRollout = removeRollout;
	}

	@PostMapping
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Create a feature flag", description = "Requires EDITOR or above. BOOLEAN flags always get the fixed true/false variant pair automatically (variants must be omitted); MULTIVARIANT flags require at least two variants with unique keys. A disabled FeatureFlagConfig is auto-created for every existing environment in the project.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Created.", content = @Content(schema = @Schema(implementation = FeatureFlagResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error (blank key/name, missing type, or a variants list invalid for the given type).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "A flag with this key already exists in this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> create(@PathVariable @P("projectKey") String projectKey,
			@Valid @RequestBody CreateFeatureFlagRequest request, @AuthenticationPrincipal UserPrincipal principal) {
		List<Variant> variants = request.variants() == null ? null
				: request.variants().stream()
						.map(v -> new Variant(null, v.key(), v.name(), v.value(), 0)).toList();

		return Map.of("data", toMap(createFeatureFlag.execute(projectKey, request.key(), request.name(),
				request.description(), request.type(), variants, principal.getId())));
	}

	@GetMapping
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).VIEWER)")
	@Operation(summary = "List feature flags", description = "Requires VIEWER or above. Each flag includes its per-environment configuration.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = FeatureFlagResponseSchema.class)))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Lacks VIEWER access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> list(@PathVariable @P("projectKey") String projectKey) {
		return Map.of("data", listFeatureFlags.execute(projectKey).stream().map(this::toMap).toList());
	}

	@GetMapping("/{flagKey}")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).VIEWER)")
	@Operation(summary = "Get a feature flag", description = "Requires VIEWER or above.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = FeatureFlagResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Lacks VIEWER access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = COMMON_404, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> get(@PathVariable @P("projectKey") String projectKey, @PathVariable String flagKey) {
		return Map.of("data", toMap(getFeatureFlag.execute(projectKey, flagKey)));
	}

	@PatchMapping("/{flagKey}")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Update a feature flag's name/description", description = "Requires EDITOR or above. Does not touch type, variants, status, or any per-environment configuration.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated.", content = @Content(schema = @Schema(implementation = FeatureFlagResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error (blank name).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = COMMON_404, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> update(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String flagKey, @Valid @RequestBody UpdateFeatureFlagRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return Map.of("data", toMap(updateFeatureFlag.execute(projectKey, flagKey, request.name(),
				request.description(), principal.getId())));
	}

	@PatchMapping("/{flagKey}/environments/{environmentKey}")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Update a flag's configuration in one environment", description = "Requires EDITOR or above. Partial update: either field may be omitted (null), but at least one must be present. Bumps the config's version.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated.", content = @Content(schema = @Schema(implementation = FeatureFlagResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error, or defaultVariantId does not belong to this flag.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = COMMON_404, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> updateEnvironment(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String flagKey, @PathVariable String environmentKey,
			@Valid @RequestBody UpdateFlagEnvironmentConfigRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return Map.of("data", toMap(updateEnvironmentConfig.execute(projectKey, flagKey, environmentKey,
				request.enabled(), request.defaultVariantId(), principal.getId())));
	}

	@PostMapping("/{flagKey}/retire")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Retire a feature flag", description = "Requires EDITOR or above. Terminal - an already-retired flag cannot be retired again. Cascades to cancel any active approval requests/experiments referencing this flag.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Retired.", content = @Content(schema = @Schema(implementation = FeatureFlagResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = COMMON_404, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "The flag is already retired.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> retire(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String flagKey, @AuthenticationPrincipal UserPrincipal principal) {
		return Map.of("data", toMap(retireFeatureFlag.execute(projectKey, flagKey, principal.getId())));
	}

	@PostMapping("/{flagKey}/environments/{environmentKey}/targeting-rules")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Add a targeting rule", tags = "Targeting", description = "Requires EDITOR or above. IF all conditions match THEN serve variantId; rules are evaluated in ascending priority order, before any rollout. variantId must belong to this flag; a SEGMENT_MATCH condition's segment id must be a real, active segment in this project.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Added.", content = @Content(schema = @Schema(implementation = FeatureFlagResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error, an unknown variantId, or an invalid/retired segment reference.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = COMMON_404, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> addTargetingRule(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String flagKey, @PathVariable String environmentKey,
			@Valid @RequestBody TargetingRuleRequest request, @AuthenticationPrincipal UserPrincipal principal) {
		List<Condition> conditions = SegmentResource.toConditions(request.conditions());

		return Map.of("data", toMap(addTargetingRule.execute(projectKey, flagKey, environmentKey, request.priority(),
				conditions, request.variantId(), principal.getId())));
	}

	@PatchMapping("/{flagKey}/environments/{environmentKey}/targeting-rules/{ruleId}")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Replace a targeting rule", tags = "Targeting", description = "Requires EDITOR or above. Whole-rule replacement (priority, conditions, and variantId all change together); the rule id and its position among other rules by priority are otherwise unaffected.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated.", content = @Content(schema = @Schema(implementation = FeatureFlagResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error, an unknown variantId, or an invalid/retired segment reference.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project/flag/environment/rule with that key/id.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> updateTargetingRule(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String flagKey, @PathVariable String environmentKey,
			@Parameter(description = "The targeting rule's id (see the rule's own `id` field, not its variantId).") @PathVariable String ruleId,
			@Valid @RequestBody TargetingRuleRequest request, @AuthenticationPrincipal UserPrincipal principal) {
		List<Condition> conditions = SegmentResource.toConditions(request.conditions());

		return Map.of("data", toMap(updateTargetingRule.execute(projectKey, flagKey, environmentKey, ruleId,
				request.priority(), conditions, request.variantId(), principal.getId())));
	}

	@DeleteMapping("/{flagKey}/environments/{environmentKey}/targeting-rules/{ruleId}")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Remove a targeting rule", tags = "Targeting", description = "Requires EDITOR or above.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Removed.", content = @Content(schema = @Schema(implementation = FeatureFlagResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project/flag/environment/rule with that key/id.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> removeTargetingRule(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String flagKey, @PathVariable String environmentKey,
			@Parameter(description = "The targeting rule's id.") @PathVariable String ruleId,
			@AuthenticationPrincipal UserPrincipal principal) {
		return Map.of("data", toMap(
				removeTargetingRule.execute(projectKey, flagKey, environmentKey, ruleId, principal.getId())));
	}

	@PutMapping("/{flagKey}/environments/{environmentKey}/rollout")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Replace the progressive rollout", tags = "Rollouts", description = "Requires EDITOR or above. Replaces the complete allocation list - no partial-update shape exists, since allocations must sum to exactly 10000 basis points (100%). Used as a fallback only when no targeting rule matches. Rollout evaluation is deterministic (SHA-256 hash of environmentId:flagKey:userKey) and never persisted per-user.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Replaced.", content = @Content(schema = @Schema(implementation = FeatureFlagResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error, an unknown/duplicate variantId, or allocations that do not sum to exactly 10000.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = COMMON_404, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> setRollout(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String flagKey, @PathVariable String environmentKey,
			@Valid @RequestBody RolloutRequest request, @AuthenticationPrincipal UserPrincipal principal) {
		List<AllocationInput> allocations = request.allocations().stream()
				.map(a -> new AllocationInput(a.variantId(), a.percentage())).toList();

		return Map.of("data", toMap(
				setRollout.execute(projectKey, flagKey, environmentKey, allocations, principal.getId())));
	}

	@DeleteMapping("/{flagKey}/environments/{environmentKey}/rollout")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	@Operation(summary = "Remove the progressive rollout", tags = "Rollouts", description = "Requires EDITOR or above. Reverts to plain defaultVariantId fallback behavior when no targeting rule matches.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Removed.", content = @Content(schema = @Schema(implementation = FeatureFlagResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks EDITOR access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = COMMON_404, content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> removeRollout(@PathVariable @P("projectKey") String projectKey,
			@PathVariable String flagKey, @PathVariable String environmentKey,
			@AuthenticationPrincipal UserPrincipal principal) {
		return Map.of("data",
				toMap(removeRollout.execute(projectKey, flagKey, environmentKey, principal.getId())));
	}

	private Map<String, Object> toMap(FeatureFlagView view) {
		Map<String, String> environmentKeysById = view.environments().stream()
				.collect(Collectors.toMap(EnvironmentRef::id, EnvironmentRef::key));

		Map<String, Object> body = toMap(view.flag());
		body.put("environments", view.configs().stream()
				.map(config -> toMap(config, environmentKeysById.get(config.getEnvironmentId()))).toList());

		return body;
	}

	private Map<String, Object> toMap(FeatureFlag flag) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", flag.getId());
		body.put("projectId", flag.getProjectId());
		body.put("key", flag.getKey());
		body.put("name", flag.getName());
		body.put("description", flag.getDescription());
		body.put("type", flag.getType());
		body.put("status", flag.getStatus());
		body.put("variants", flag.getVariants().stream().map(this::toMap).toList());
		body.put("createdBy", flag.getCreatedBy());
		body.put("createdAt", flag.getCreatedAt());
		body.put("updatedBy", flag.getUpdatedBy());
		body.put("updatedAt", flag.getUpdatedAt());

		return body;
	}

	private Map<String, Object> toMap(Variant variant) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", variant.id());
		body.put("key", variant.key());
		body.put("name", variant.name());
		body.put("value", variant.value());
		body.put("order", variant.order());

		return body;
	}

	private Map<String, Object> toMap(FeatureFlagConfig config, String environmentKey) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", config.getId());
		body.put("featureFlagId", config.getFeatureFlagId());
		body.put("environmentId", config.getEnvironmentId());
		body.put("enabled", config.isEnabled());
		body.put("defaultVariantId", config.getDefaultVariantId());
		body.put("targetingRules", config.getTargetingRules().stream().map(this::toMap).toList());
		body.put("rollout", config.getRollout() == null ? null : toMap(config.getRollout()));
		body.put("version", config.getVersion());
		body.put("updatedBy", config.getUpdatedBy());
		body.put("updatedAt", config.getUpdatedAt());
		body.put("createdAt", config.getCreatedAt());
		body.put("environmentKey", environmentKey);

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
