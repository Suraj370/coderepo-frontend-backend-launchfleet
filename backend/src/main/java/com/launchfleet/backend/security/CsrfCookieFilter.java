package com.launchfleet.backend.security;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security 6+ resolves the CSRF token lazily - CookieCsrfTokenRepository only
 * writes the XSRF-TOKEN cookie once something actually reads CsrfToken.getToken(). A pure
 * JSON API never does that on its own (no server-rendered form touches it), so without
 * this filter the cookie a client needs in order to make its *next* request would never
 * get set. This is the standard fix from Spring's own "CSRF for a SPA" guide: force the
 * deferred token to resolve on every request that reaches the dashboard chain.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());

		if (csrfToken != null) {
			csrfToken.getToken();
		}

		chain.doFilter(request, response);
	}
}
