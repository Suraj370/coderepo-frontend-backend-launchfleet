package com.launchfleet.backend.featureflags.ports;

import java.util.List;
import java.util.Optional;

public interface EnvironmentLookup {

	List<EnvironmentRef> findByProjectId(String projectId);

	Optional<EnvironmentRef> findByProjectIdAndKey(String projectId, String key);

	/** Added for the approvals module (Phase 6): an approval request stores environmentId, not a key. */
	Optional<EnvironmentRef> findById(String id);

}
