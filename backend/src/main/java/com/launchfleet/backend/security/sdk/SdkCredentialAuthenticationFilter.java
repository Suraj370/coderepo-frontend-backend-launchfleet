package com.launchfleet.backend.security.sdk;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.launchfleet.backend.sdk.SdkCredentialService;
import com.launchfleet.backend.security.SecurityConstants;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Registered only on the SDK SecurityFilterChain (see SecurityConfig) - it never runs
 * for dashboard requests, and never reads cookies/HttpSession. Pure Spring Security
 * wiring: extract the bearer credential, delegate validation to SdkCredentialService
 * (the only place with SDK credential business/DB logic), build the Authentication,
 * establish the security context, continue. Leaves the request unauthenticated on any
 * miss; authorizeHttpRequests + the JSON AuthenticationEntryPoint handle the 401.
 */
public class SdkCredentialAuthenticationFilter extends OncePerRequestFilter {

	private final SdkCredentialService sdkCredentialService;

	public SdkCredentialAuthenticationFilter(SdkCredentialService sdkCredentialService) {
		this.sdkCredentialService = sdkCredentialService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		readBearerToken(request).flatMap(sdkCredentialService::authenticate).ifPresent(credential -> {
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(new SdkAuthenticationToken(credential));
			SecurityContextHolder.setContext(context);
		});

		chain.doFilter(request, response);
	}

	private Optional<String> readBearerToken(HttpServletRequest request) {
		String header = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);

		if (header == null || !header.startsWith(SecurityConstants.BEARER_PREFIX)) {
			return Optional.empty();
		}

		String token = header.substring(SecurityConstants.BEARER_PREFIX.length()).trim();

		return token.isEmpty() ? Optional.empty() : Optional.of(token);
	}
}
