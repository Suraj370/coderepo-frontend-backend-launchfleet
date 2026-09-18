package com.launchfleet.backend.environments.application;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.shared.ApiException;

/**
 * The standard environment set (development/staging/production) is a provisioning
 * convention, not a domain constraint - Environment itself stays generic (any key is
 * a valid environment; see CreateEnvironment), and this is just the reusable "give a
 * project the usual starting set" operation. Used by the seed script today; available
 * for a future project-onboarding flow to reuse without duplicating this list.
 * Idempotent: an already-existing standard key is skipped rather than failing the
 * whole call, so re-running this against a project that already has some of the
 * three is safe.
 */
@Component
public class ProvisionStandardEnvironments {

	private static final Map<String, String> STANDARD_ENVIRONMENTS = standardEnvironments();

	private final CreateEnvironment createEnvironment;

	public ProvisionStandardEnvironments(CreateEnvironment createEnvironment) {
		this.createEnvironment = createEnvironment;
	}

	public void execute(String projectId, String actingUserId) {
		for (Map.Entry<String, String> entry : STANDARD_ENVIRONMENTS.entrySet()) {
			try {
				createEnvironment.execute(projectId, entry.getKey(), entry.getValue(), actingUserId);
			} catch (ApiException exception) {
				if (exception.getStatusCode() != 409) {
					throw exception;
				}
				// Already provisioned - fine, this call is idempotent by design.
			}
		}
	}

	private static Map<String, String> standardEnvironments() {
		Map<String, String> environments = new LinkedHashMap<>();
		environments.put("development", "Development");
		environments.put("staging", "Staging");
		environments.put("production", "Production");

		return environments;
	}
}
