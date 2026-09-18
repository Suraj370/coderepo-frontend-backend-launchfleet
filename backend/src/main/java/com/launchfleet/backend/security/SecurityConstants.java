package com.launchfleet.backend.security;

/**
 * Single source of truth for the literal values that define the two security
 * boundaries - authority names, the session cookie name, and the URL matchers each
 * SecurityFilterChain claims - so the chain configuration and anything that needs to
 * agree with it (tests, filters, tokens) can't silently drift apart.
 */
public final class SecurityConstants {

	// Dashboard authentication only proves "this is an authenticated user"; per-project
	// permissions are checked fresh per request by ProjectAccessService, not cached here.
	public static final String DASHBOARD_USER_AUTHORITY = "ROLE_USER";

	public static final String SDK_SERVER_AUTHORITY = "SDK_SERVER";

	public static final String SDK_CLIENT_SIDE_AUTHORITY = "SDK_CLIENT_SIDE";

	// Spring Session's cookie name, not the servlet container's JSESSIONID.
	public static final String SESSION_COOKIE_NAME = "SESSION";

	public static final String AUTHORIZATION_HEADER = "Authorization";

	public static final String BEARER_PREFIX = "Bearer ";

	public static final String SDK_MATCHER = "/api/v1/sdk/**";

	public static final String DASHBOARD_MATCHER = "/api/v1/**";

	public static final String LOGIN_PATH = "/api/v1/auth/login";

	public static final String REGISTER_PATH = "/api/v1/auth/register";

	public static final String LOGOUT_PATH = "/api/v1/auth/logout";

	public static final String ACTUATOR_HEALTH_PATH = "/actuator/health";

	// Springdoc's own routes - Swagger UI and the OpenAPI JSON/YAML document (see
	// OpenApiConfig). Always permitted here regardless of environment; whether they exist at
	// all is controlled by springdoc.api-docs.enabled/springdoc.swagger-ui.enabled
	// (application.yaml disables both by default, application-dev.yaml re-enables them) - see
	// SecurityConfig.defaultDenyFilterChain's Javadoc.
	public static final String API_DOCS_MATCHER = "/v3/api-docs/**";

	public static final String SWAGGER_UI_MATCHER = "/swagger-ui/**";

	public static final String SWAGGER_UI_HTML_PATH = "/swagger-ui.html";

	private SecurityConstants() {
	}
}
