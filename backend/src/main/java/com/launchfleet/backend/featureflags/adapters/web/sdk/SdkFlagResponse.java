package com.launchfleet.backend.featureflags.adapters.web.sdk;

import java.util.List;

/**
 * version here is the flag's own FeatureFlagConfig.version - included for per-flag
 * diagnostics only. The SDK's own refresh/staleness decision uses the top-level
 * SdkConfigurationResponse.version (see GetSdkConfiguration), never this field.
 */
public record SdkFlagResponse(String key, boolean enabled, SdkVariantResponse defaultVariant,
		List<SdkVariantResponse> variants, List<SdkTargetingRuleResponse> targetingRules, SdkRolloutResponse rollout,
		int version) {
}
