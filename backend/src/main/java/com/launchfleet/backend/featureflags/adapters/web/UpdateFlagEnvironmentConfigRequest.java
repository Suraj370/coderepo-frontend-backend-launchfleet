package com.launchfleet.backend.featureflags.adapters.web;

/** Partial update - either field may be omitted (null); at least one must be present. */
public record UpdateFlagEnvironmentConfigRequest(

		Boolean enabled,

		String defaultVariantId) {
}
