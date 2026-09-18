package com.launchfleet.backend.sdk.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.launchfleet.backend.projects.ProjectRepository;
import com.launchfleet.backend.sdk.SdkCredential;
import com.launchfleet.backend.sdk.SdkCredentialRepository;
import com.launchfleet.backend.sdk.SdkCredentialService;
import com.launchfleet.backend.sdk.SdkCredentialType;
import com.launchfleet.backend.sdk.application.CreateSdkCredential;
import com.launchfleet.backend.shared.ApiException;
import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.SdkCredentialResponseSchema;

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
 * Dashboard-facing management of SDK credentials (issuing/revoking API keys) -
 * distinct from SdkCredentialAuthenticationFilter, which authenticates SDK
 * callers using these credentials on the separate sdkFilterChain. Requires
 * ADMIN throughout: issuing or revoking a secret is sensitive.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/api-keys")
@Tag(name = "API Keys", description = "SDK credentials (server keys and client-side ids) for this project.")
@SecurityRequirement(name = OpenApiConfig.DASHBOARD_SESSION_SCHEME)
public class SdkCredentialResource {

	private static final int NOT_FOUND = 404;

	private final CreateSdkCredential createSdkCredential;

	private final SdkCredentialService sdkCredentialService;

	private final SdkCredentialRepository sdkCredentialRepository;

	private final ProjectRepository projectRepository;

	public SdkCredentialResource(CreateSdkCredential createSdkCredential, SdkCredentialService sdkCredentialService,
			SdkCredentialRepository sdkCredentialRepository, ProjectRepository projectRepository) {
		this.createSdkCredential = createSdkCredential;
		this.sdkCredentialService = sdkCredentialService;
		this.sdkCredentialRepository = sdkCredentialRepository;
		this.projectRepository = projectRepository;
	}

	@GetMapping
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).ADMIN)")
	@Operation(summary = "List this project's API keys", description = "Requires ADMIN. Server key secrets are never returned after creation - only label/type/environment/active/createdAt.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SdkCredentialResponseSchema.class)))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Lacks ADMIN access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> list(@PathVariable @P("projectKey") String projectKey) {
		resolveProject(projectKey);

		return Map.of("data",
				sdkCredentialRepository.findByProjectKey(projectKey).stream().map(c -> toMap(c, null)).toList());
	}

	@PostMapping
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).ADMIN)")
	@Operation(summary = "Issue a new API key", description = "Requires ADMIN. The plaintext secret (SERVER) or client-side id (CLIENT_SIDE) is returned in this response only - it cannot be retrieved again afterward.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Issued.", content = @Content(schema = @Schema(implementation = SdkCredentialResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks ADMIN access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> create(@PathVariable @P("projectKey") String projectKey,
			@Valid @RequestBody CreateSdkCredentialRequest request) {
		resolveProject(projectKey);
		CreateSdkCredential.Result result = createSdkCredential.execute(projectKey, request.environmentKey(),
				request.type(), request.label());

		return Map.of("data", toMap(result.credential(), result.plaintext()));
	}

	@PostMapping("/{id}/revoke")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).ADMIN)")
	@Operation(summary = "Revoke an API key", description = "Requires ADMIN. Terminal - a revoked credential can no longer authenticate.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Revoked.", content = @Content(schema = @Schema(implementation = SdkCredentialResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token, or lacks ADMIN access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project or API key with that id in this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> revoke(@PathVariable @P("projectKey") String projectKey, @PathVariable String id) {
		resolveProject(projectKey);
		SdkCredential credential = sdkCredentialRepository.findById(id)
				.filter(candidate -> candidate.getProjectKey().equals(projectKey))
				.orElseThrow(() -> new ApiException(NOT_FOUND, "API_KEY_NOT_FOUND",
						"No API key with that id in this project."));

		sdkCredentialService.revoke(credential);

		return Map.of("data", toMap(credential, null));
	}

	private void resolveProject(String projectKey) {
		if (projectRepository.findByKey(projectKey).isEmpty()) {
			throw new ApiException(NOT_FOUND, "PROJECT_NOT_FOUND", "No project with that key.");
		}
	}

	private Map<String, Object> toMap(SdkCredential credential, String plaintextSecret) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", credential.getId());
		body.put("environmentKey", credential.getEnvironmentKey());
		body.put("type", credential.getType());
		body.put("label", credential.getLabel());
		body.put("clientSideId",
				credential.getType() == SdkCredentialType.CLIENT_SIDE ? credential.getClientSideId() : null);
		body.put("active", credential.isActive());
		body.put("createdAt", credential.getCreatedAt());
		body.put("revokedAt", credential.getRevokedAt());
		body.put("expiresAt", credential.getExpiresAt());
		if (plaintextSecret != null) {
			body.put("plaintextSecret", plaintextSecret);
		}

		return body;
	}
}
