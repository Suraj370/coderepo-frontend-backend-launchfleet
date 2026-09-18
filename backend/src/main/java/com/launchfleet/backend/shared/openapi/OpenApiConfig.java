package com.launchfleet.backend.shared.openapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Springdoc OpenAPI configuration - documentation only, never referenced by any
 * controller's business logic. Declares the security schemes that match this
 * app's actual SecurityConfig exactly (see its Javadoc):
 *
 *  - dashboardSession: the SESSION cookie established by POST /api/v1/auth/login,
 *    checked on the dashboard SecurityFilterChain (/api/v1/**, excluding
 *    /api/v1/sdk/**). Per-project RBAC (VIEWER/EDITOR/ADMIN) is layered on top via
 *    @PreAuthorize, documented per-operation.
 *  - dashboardCsrf: the double-submit X-XSRF-TOKEN header required by that same
 *    chain's CookieCsrfTokenRepository for every state-changing (non-GET) dashboard
 *    request. Defined here as a real scheme so it appears in the security-scheme
 *    list, but deliberately NOT also attached as a per-operation @SecurityRequirement
 *    everywhere it applies: swagger-core only keeps ONE @SecurityRequirement source
 *    (method overrides class rather than adding to it) per operation, and every
 *    dashboard resource already carries a class-level dashboardSession requirement -
 *    adding a second, method-level dashboardCsrf requirement was found to silently
 *    replace nothing and simply be dropped (verified against the generated spec, not
 *    assumed), which would have been worse than not declaring it at all. Each
 *    write operation's own description/403 response instead states the CSRF
 *    requirement explicitly in words - see e.g. FeatureFlagResource.create.
 *  - sdkServerKey: the "Authorization: Bearer <token>" SDK_SERVER credential
 *    required by the SDK SecurityFilterChain (/api/v1/sdk/**) by default.
 *  - sdkClientSideKey: the SDK_CLIENT_SIDE credential type. Defined here for
 *    completeness (locked SecurityConstants.SDK_CLIENT_SIDE_AUTHORITY exists), but
 *    deliberately NOT attached to any operation below - no current endpoint accepts
 *    it (see SecurityConfig.sdkFilterChain's Javadoc: it reaches nothing by default
 *    until a future route explicitly opts it in).
 */
@Configuration
public class OpenApiConfig {

	public static final String DASHBOARD_SESSION_SCHEME = "dashboardSession";

	public static final String DASHBOARD_CSRF_SCHEME = "dashboardCsrf";

	public static final String SDK_SERVER_SCHEME = "sdkServerKey";

	public static final String SDK_CLIENT_SIDE_SCHEME = "sdkClientSideKey";

	@Bean
	public OpenAPI launchFleetOpenApi() {
		return new OpenAPI()
				.info(new Info().title("LaunchFleet API").version("v1").description(
						"REST API for the LaunchFleet feature-flag/experimentation platform: projects/environments, "
								+ "feature flags with targeting rules and progressive rollouts, segments, an "
								+ "approval workflow, experimentation and metrics, and the SDK-facing configuration/"
								+ "event endpoints. Every successful response is wrapped as {\"data\": <result>}; "
								+ "every error response uses the single ErrorResponseSchema envelope documented "
								+ "on this page's schemas."))
				.components(new Components()
						.addSecuritySchemes(DASHBOARD_SESSION_SCHEME,
								new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE)
										.name("SESSION")
										.description(
												"Dashboard authentication. Obtained via POST /api/v1/auth/login, "
														+ "which sets this HttpOnly session cookie. Required by every "
														+ "/api/v1/** dashboard endpoint (everything except /api/v1/sdk/**)."))
						.addSecuritySchemes(DASHBOARD_CSRF_SCHEME,
								new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
										.name("X-XSRF-TOKEN")
										.description(
												"Double-submit CSRF token required on every non-GET dashboard request. "
														+ "The value must match the XSRF-TOKEN cookie issued by any prior "
														+ "GET request on the dashboard chain."))
						.addSecuritySchemes(SDK_SERVER_SCHEME,
								new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
										.description(
												"SDK_SERVER credential (confidential, server-side only). Sent as "
														+ "\"Authorization: Bearer <server-key>\". Required by every "
														+ "/api/v1/sdk/** endpoint by default."))
						.addSecuritySchemes(SDK_CLIENT_SIDE_SCHEME,
								new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
										.description(
												"SDK_CLIENT_SIDE credential (public, safe to embed in browser JS). "
														+ "Authenticates successfully but is not currently authorized for "
														+ "any endpoint in this API - the SDK filter chain requires "
														+ "SDK_SERVER specifically unless a route explicitly opts into "
														+ "this scheme, which none currently do.")));
	}
}
