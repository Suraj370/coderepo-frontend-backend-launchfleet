package com.launchfleet.backend.featureflags.adapters.web.sdk;

import java.util.List;

public record SdkTargetingRuleResponse(String id, int priority, List<SdkConditionResponse> conditions,
		String variantId) {
}
