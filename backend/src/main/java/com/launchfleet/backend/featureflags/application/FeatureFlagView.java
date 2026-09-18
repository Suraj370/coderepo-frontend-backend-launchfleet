package com.launchfleet.backend.featureflags.application;

import java.util.List;

import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;

/**
 * What every single-flag use case (create/get/update/retire/updateEnvironmentConfig)
 * returns: the flag, its configs, and enough environment reference data for the web
 * adapter to render environment keys - plain data, no HTTP/JSON shaping decisions.
 */
public record FeatureFlagView(FeatureFlag flag, List<FeatureFlagConfig> configs, List<EnvironmentRef> environments) {
}
