package com.launchfleet.backend.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.launchfleet.backend.shared.GlobalExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs before the DispatcherServlet, so @RestControllerAdvice never sees this -
 * writes the same {"error": {code, message}} envelope by hand instead of letting
 * Spring Security's default entry point render a 401 with no body.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private static final int UNAUTHORIZED = 401;

	private final ObjectMapper objectMapper;

	public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response.setStatus(UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getWriter(),
				GlobalExceptionHandler.envelope("AUTH_REQUIRED", "Authentication is required.", null));
	}
}
