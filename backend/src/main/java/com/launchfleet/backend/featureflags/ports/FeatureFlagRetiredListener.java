package com.launchfleet.backend.featureflags.ports;

/**
 * Mirrors environments.ports.EnvironmentCreatedListener's pattern: featureflags/
 * defines the contract, a consuming module (approvals/, Phase 6) implements it and
 * registers as a Spring bean. RetireFeatureFlag notifies every listener after a
 * successful retirement, so featureflags/ never needs to know approvals/ exists.
 */
public interface FeatureFlagRetiredListener {

	void onFeatureFlagRetired(String projectId, String featureFlagId, String flagKey, String actingUserId);

}
