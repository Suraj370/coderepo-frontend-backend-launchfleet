package com.launchfleet.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import com.launchfleet.backend.sdk.SdkCredentialService;
import com.launchfleet.backend.security.sdk.SdkCredentialAuthenticationFilter;

/**
 * Explicit Spring Security configuration - Boot's default security auto-configuration
 * is never relied on. Three ordered SecurityFilterChain beans keep the SDK and dashboard
 * boundaries structurally separate (different filters, different Authentication types,
 * different authority namespaces) plus a default-deny catch-all so nothing outside
 * /api/v1/** is accidentally left unauthenticated by omission. All three share the same
 * JSON AuthenticationEntryPoint/AccessDeniedHandler so every 401/403 in the app has the
 * same error envelope shape, regardless of which chain (or which layer - filter-level
 * authorizeHttpRequests vs method-level @PreAuthorize, see GlobalExceptionHandler) denied it.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/**
	 * AuthService's login is a hand-rolled JSON endpoint, not Spring Security's standard
	 * AbstractAuthenticationProcessingFilter flow - so the session-fixation protection that
	 * flow triggers automatically never runs for it. Exposed as a shared bean, wired into
	 * both the dashboard chain below (sessionAuthenticationStrategy(...)) and AuthService,
	 * so login explicitly invokes the exact same strategy by hand, right after authenticating
	 * and before the new SecurityContext is saved - see AuthService.login.
	 */
	@Bean
	public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
		return new ChangeSessionIdAuthenticationStrategy();
	}

	@Bean
	public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);

		return new ProviderManager(provider);
	}

	@Bean
	public SdkCredentialAuthenticationFilter sdkCredentialAuthenticationFilter(
			SdkCredentialService sdkCredentialService) {
		return new SdkCredentialAuthenticationFilter(sdkCredentialService);
	}

	/**
	 * SDK boundary: stateless, header-only, never touches cookies/HttpSession. Defaults to
	 * requiring SDK_SERVER specifically, not "any SDK credential" - a client-side id
	 * authenticates (it proves which project/environment) but reaches nothing here by
	 * default. See SdkCredentialType's Javadoc for why that asymmetry is intentional: a
	 * client-side id is meant to be public, so it must not inherit a secret credential's
	 * default trust. A future route can narrow back to SDK_CLIENT_SIDE explicitly once it
	 * implements an operation that's actually safe to expose that way.
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain sdkFilterChain(HttpSecurity http, SdkCredentialAuthenticationFilter sdkFilter,
			JsonAuthenticationEntryPoint entryPoint, JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
		http.securityMatcher(SecurityConstants.SDK_MATCHER)
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(sdkFilter, UsernamePasswordAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(SecurityConstants.SDK_SERVER_AUTHORITY))
				.exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable);

		return http.build();
	}

	/** Dashboard boundary: cookie + HttpSession, RBAC enforced per-project via @PreAuthorize. */
	@Bean
	@Order(2)
	public SecurityFilterChain dashboardFilterChain(HttpSecurity http,
			SecurityContextRepository securityContextRepository,
			SessionAuthenticationStrategy sessionAuthenticationStrategy, JsonAuthenticationEntryPoint entryPoint,
			JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
		http.securityMatcher(SecurityConstants.DASHBOARD_MATCHER)
				.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
						// Plain (non-XOR) handler: the frontend reads the raw XSRF-TOKEN cookie
						// value and echoes it verbatim as the X-XSRF-TOKEN header - there's no
						// server-rendered form here that would need BREACH-resistant encoding.
						.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
				.addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
						// Wires the same strategy bean AuthService.login invokes by hand (our
						// login bypasses the standard filter that would otherwise trigger this
						// automatically - see the bean's Javadoc).
						.sessionAuthenticationStrategy(sessionAuthenticationStrategy))
				.securityContext(sc -> sc.securityContextRepository(securityContextRepository))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, SecurityConstants.LOGIN_PATH,
								SecurityConstants.REGISTER_PATH)
						.permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
				.logout(logout -> logout.logoutUrl(SecurityConstants.LOGOUT_PATH)
						.deleteCookies(SecurityConstants.SESSION_COOKIE_NAME)
						.logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable);

		return http.build();
	}

	/**
	 * Default-deny catch-all. Without this, any path outside /api/v1/** (e.g.
	 * /actuator/**) would bypass Spring Security entirely once Boot's own default
	 * security auto-configuration is turned off, since no SecurityFilterChain's
	 * matcher would claim the request - confirmed empirically, not assumed. Kept as a
	 * third chain rather than folded into chain 2, because chain 2's matcher is scoped
	 * to /api/v1/** specifically; widening it to anyRequest() would blur the "which
	 * chain is authoritative for this path" boundary the other two chains depend on
	 * being unambiguous.
	 *
	 * Also permits springdoc's own routes (Swagger UI, OpenAPI JSON/YAML) - harmless to
	 * permit unconditionally here, since whether those routes exist at all is controlled
	 * separately by springdoc.api-docs.enabled/springdoc.swagger-ui.enabled (off by default,
	 * on only under the dev profile - see application.yaml/application-dev.yaml). This never
	 * changes behavior for any existing /api/v1/** or /actuator/health request.
	 */
	@Bean
	@Order(3)
	public SecurityFilterChain defaultDenyFilterChain(HttpSecurity http, JsonAuthenticationEntryPoint entryPoint,
			JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
		http.securityMatcher("/**")
				.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, SecurityConstants.ACTUATOR_HEALTH_PATH).permitAll()
						.requestMatchers(HttpMethod.GET, SecurityConstants.API_DOCS_MATCHER,
								SecurityConstants.SWAGGER_UI_MATCHER, SecurityConstants.SWAGGER_UI_HTML_PATH)
						.permitAll()
						.anyRequest().denyAll())
				.exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable);

		return http.build();
	}
}
