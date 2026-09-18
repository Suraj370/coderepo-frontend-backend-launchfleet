package com.launchfleet.backend.shared;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final int INTERNAL_STATUS = 500;

	private static final String INTERNAL_CODE = "INTERNAL_ERROR";

	private static final String INTERNAL_MESSAGE = "An unexpected error occurred.";

	private static final int NOT_FOUND_STATUS = 404;

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Map<String, Object>> handleApiException(ApiException exception) {
		return ResponseEntity.status(exception.getStatusCode())
				.body(envelope(exception.getCode(), exception.getMessage(), exception.getDetails()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
		Map<String, Object> details = new LinkedHashMap<>();

		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			details.put(fieldError.getField(), fieldError.getDefaultMessage());
		}

		return ResponseEntity.status(400).body(envelope("VALIDATION_ERROR", "Check the highlighted fields.", details));
	}

	// Malformed JSON or an invalid enum value (e.g. an unknown FlagType) throws this from
	// Jackson during request deserialization, before any @Valid check runs - without a
	// handler it falls through to the generic 500 below instead of a 400.
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> handleMalformedRequest(HttpMessageNotReadableException exception) {
		return ResponseEntity.status(400)
				.body(envelope("MALFORMED_REQUEST", "The request body could not be read.", null));
	}

	// @PreAuthorize failures (e.g. AuthorizationDeniedException) are thrown from inside the
	// AOP-advised controller method, inside DispatcherServlet's own dispatch - they never pass
	// through ExceptionTranslationFilter/the JSON AccessDeniedHandler in SecurityConfig, which
	// only sees denials from authorizeHttpRequests' filter-level checks. Handled here instead.
	// Domain constructors/factories throw this for invariant violations (e.g. Condition's
	// compact constructor) and application use cases already catch-and-remap their own
	// calls to ApiException(400) - but some domain objects (e.g. Condition, built directly
	// from a request DTO in a resource's mapping helper before any use case runs) are
	// constructed outside that try/catch. This is the safety net for those escapees, so a
	// bad request shape still surfaces as 400 VALIDATION_ERROR instead of a bare 500.
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.status(400).body(envelope("VALIDATION_ERROR", exception.getMessage(), null));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException exception) {
		return ResponseEntity.status(403)
				.body(envelope("FORBIDDEN", "You do not have permission to do that.", null));
	}

	@ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class,
			HttpRequestMethodNotSupportedException.class })
	public ResponseEntity<Map<String, Object>> handleMissingRoute(HttpServletRequest request) {
		String message = "Route " + request.getMethod() + " " + request.getRequestURI() + " was not found.";

		return ResponseEntity.status(NOT_FOUND_STATUS).body(envelope("NOT_FOUND", message, null));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
		// Logged via the throwable argument (full stack trace in application logs for
		// diagnosis), never echoed into the response body - the client only gets a
		// generic message, matching "no internal details in API responses."
		LOGGER.error("Unhandled error", exception);

		return ResponseEntity.status(INTERNAL_STATUS).body(envelope(INTERNAL_CODE, INTERNAL_MESSAGE, null));
	}

	public static Map<String, Object> envelope(String code, String message, Map<String, Object> details) {
		Map<String, Object> error = new LinkedHashMap<>();
		error.put("code", code);
		error.put("message", message);

		if (details != null) {
			error.put("details", details);
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", error);

		return body;
	}
}
