package com.launchfleet.backend.security;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only fixture (never shipped in main) exercising the two SecurityFilterChains and
 * the per-project @PreAuthorize expression, since no real business endpoints exist yet
 * for this increment to protect (see the plan's "what is not built" section).
 */
@RestController
@RequestMapping("/api/v1")
class TestFixtureController {

	@GetMapping("/projects/{projectKey}/ping")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).VIEWER)")
	Map<String, Object> viewerPing(@PathVariable @P("projectKey") String projectKey) {
		return Map.of("ok", true);
	}

	@PostMapping("/projects/{projectKey}/ping")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).EDITOR)")
	Map<String, Object> editorPing(@PathVariable @P("projectKey") String projectKey) {
		return Map.of("ok", true);
	}

	@GetMapping("/sdk/ping")
	@PreAuthorize("hasAuthority('SDK_SERVER')")
	Map<String, Object> sdkServerPing() {
		return Map.of("ok", true);
	}
}
