package com.launchfleet.backend.users.web;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.UserResponseSchema;
import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserPrincipal;
import com.launchfleet.backend.users.application.UpdateProfile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** The current dashboard user's own profile - name only. Email/password changes are out of scope here. */
@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Users", description = "The current dashboard user's own profile.")
@SecurityRequirement(name = OpenApiConfig.DASHBOARD_SESSION_SCHEME)
public class UserResource {

	private final UpdateProfile updateProfile;

	public UserResource(UpdateProfile updateProfile) {
		this.updateProfile = updateProfile;
	}

	@GetMapping
	@Operation(summary = "Get my profile")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> me(@AuthenticationPrincipal UserPrincipal principal) {
		return Map.of("data", principal.getUser().toPublicMap());
	}

	@PatchMapping
	@Operation(summary = "Update my profile", description = "Name only - email and password changes are out of scope here.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated.", content = @Content(schema = @Schema(implementation = UserResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error (blank name).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing/incorrect CSRF token.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> updateMe(@Valid @RequestBody UpdateProfileRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		User updated = updateProfile.execute(principal.getUser(), request.name());

		return Map.of("data", updated.toPublicMap());
	}
}
