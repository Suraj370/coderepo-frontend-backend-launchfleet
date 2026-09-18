package com.launchfleet.backend.environments.ports;

/**
 * environments/ is a platform capability with no knowledge of who consumes environment
 * lifecycle events - featureflags/ (or any future module) reacts by implementing this
 * and registering as a Spring bean; CreateEnvironment notifies every listener after a
 * successful creation. This keeps the dependency pointing the correct direction: the
 * platform module defines the contract, the consuming module implements it.
 */
public interface EnvironmentCreatedListener {

	void onEnvironmentCreated(String projectId, String environmentId, String environmentKey, String actingUserId);

}
