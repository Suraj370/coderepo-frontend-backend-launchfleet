package com.launchfleet.backend.featureflags.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.environments.ports.EnvironmentCreatedListener;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.FlagStatus;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;

/**
 * The other half of the FeatureFlag/Environment invariant: when a new environment
 * is created, every ACTIVE flag already in that project gets a disabled config for
 * it - closing the synchronization gap the audit identified. Idempotent by design
 * (checks for an existing config before creating one) so it's safe if ever invoked
 * more than once for the same environment; the unique (featureFlagId, environmentId)
 * index remains the hard backstop against a genuine race producing a duplicate.
 *
 * Not wrapped in a MongoDB transaction with the environment's own creation (no
 * replica-set-backed multi-document transaction is configured for this single-node
 * setup, and none is introduced here - see the final report's consistency notes).
 * If this loop fails partway, the environment itself is already committed and some
 * flags may be missing a config; recovery today has no dedicated repair endpoint -
 * documented as a known limitation, not hidden.
 */
@Component
class EnsureConfigsForNewEnvironment implements EnvironmentCreatedListener {

	private final FeatureFlagStore featureFlagStore;

	private final FeatureFlagConfigStore featureFlagConfigStore;

	EnsureConfigsForNewEnvironment(FeatureFlagStore featureFlagStore, FeatureFlagConfigStore featureFlagConfigStore) {
		this.featureFlagStore = featureFlagStore;
		this.featureFlagConfigStore = featureFlagConfigStore;
	}

	@Override
	public void onEnvironmentCreated(String projectId, String environmentId, String environmentKey,
			String actingUserId) {
		for (FeatureFlag flag : featureFlagStore.findByProjectId(projectId)) {
			if (flag.getStatus() != FlagStatus.ACTIVE) {
				continue;
			}

			if (featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environmentId).isPresent()) {
				continue;
			}

			FeatureFlagConfig config = FeatureFlagConfig.createDisabled(flag.getId(), environmentId, projectId,
					flag.defaultVariant().id(), actingUserId);
			featureFlagConfigStore.save(config);
		}
	}
}
