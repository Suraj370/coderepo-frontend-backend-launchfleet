package com.launchfleet.backend.featureflags.ports;

/**
 * Just enough of an Environment for this module to resolve/scope by. active is
 * false once the environment has been retired (Phase 6) - reads still resolve a
 * retired environment (its history/flags remain visible), but the approvals module
 * treats it the same way a retired flag is treated: no new configuration may be
 * applied to it.
 */
public record EnvironmentRef(String id, String key, boolean active) {
}
