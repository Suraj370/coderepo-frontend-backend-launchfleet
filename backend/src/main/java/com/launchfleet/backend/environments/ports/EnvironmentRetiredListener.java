package com.launchfleet.backend.environments.ports;

/**
 * Mirrors EnvironmentCreatedListener's pattern (see its Javadoc): environments/
 * defines the contract, a consuming module (approvals/, Phase 6) implements it and
 * registers as a Spring bean. RetireEnvironment notifies every listener after a
 * successful retirement.
 */
public interface EnvironmentRetiredListener {

	void onEnvironmentRetired(String projectId, String environmentId, String environmentKey, String actingUserId);

}
