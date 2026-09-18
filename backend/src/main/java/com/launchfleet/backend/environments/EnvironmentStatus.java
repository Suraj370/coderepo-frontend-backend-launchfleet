package com.launchfleet.backend.environments;

/** Mirrors featureflags.domain.FlagStatus's shape - same two-state lifecycle, applied to Environment (Phase 6). */
public enum EnvironmentStatus {

	ACTIVE,

	RETIRED

}
