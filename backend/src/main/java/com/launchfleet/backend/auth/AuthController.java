package com.launchfleet.backend.auth;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.SessionResponseSchema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Dashboard authentication only. POST /login and POST /register are the only
 * public routes on the dashboard SecurityFilterChain (see SecurityConfig); logout
 * is handled entirely by Spring Security's LogoutFilter via the logoutUrl()
 * configuration, not a controller method here.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Dashboard session login/logout and the current session. Logout (POST /api/v1/auth/logout) is handled entirely by Spring Security's LogoutFilter, not a controller method, so it does not appear as a documented operation here.")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	@Operation(summary = "Log in to the dashboard", description = "One of the two public routes on the dashboard security chain (the other is POST /register). On success, sets the HttpOnly SESSION cookie used by every other dashboard endpoint. Requires a valid CSRF token pair (a prior GET response's XSRF-TOKEN cookie, echoed as X-XSRF-TOKEN) even though the caller is not yet authenticated.")
	@SecurityRequirement(name = OpenApiConfig.DASHBOARD_CSRF_SCHEME)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Authenticated - the session cookie is set and the current user is returned.", content = @Content(schema = @Schema(implementation = SessionResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error (missing/malformed email or password).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "Email/password incorrect, or the user is inactive (code INVALID_CREDENTIALS in both cases - the API never reveals which).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing or incorrect CSRF token.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request,
			HttpServletResponse response) {
		return Map.of("data", authService.login(request, response, loginRequest));
	}

	@PostMapping("/register")
	@Operation(summary = "Register a new dashboard account", description = "Public, like POST /login. Creates a new user (no project access - an ADMIN grants that separately) and immediately signs them in exactly as /login would, setting the same HttpOnly SESSION cookie. Requires the same CSRF token pair as /login.")
	@SecurityRequirement(name = OpenApiConfig.DASHBOARD_CSRF_SCHEME)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Registered and authenticated - the session cookie is set and the new user is returned.", content = @Content(schema = @Schema(implementation = SessionResponseSchema.class))),
			@ApiResponse(responseCode = "400", description = "Validation error (missing/malformed name, email, or password).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Missing or incorrect CSRF token.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "409", description = "An account with this email already exists (code EMAIL_TAKEN).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> register(@Valid @RequestBody RegisterRequest registerRequest,
			HttpServletRequest request, HttpServletResponse response) {
		return Map.of("data", authService.register(request, response, registerRequest));
	}

	@GetMapping("/session")
	@Operation(summary = "Get the current dashboard session", description = "Requires an existing SESSION cookie, like every other dashboard endpoint (see SecurityConfig's dashboard chain: all of /api/v1/** except POST /login and POST /register requires authentication) - it is also the standard way to obtain a fresh XSRF-TOKEN cookie before a subsequent login, register, or other state-changing call, since the CSRF cookie filter runs before the authentication check rejects an anonymous request.")
	@SecurityRequirement(name = OpenApiConfig.DASHBOARD_SESSION_SCHEME)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "A session is active.", content = @Content(schema = @Schema(implementation = SessionResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No active session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> session() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		return Map.of("data", authService.session(authentication));
	}
}
