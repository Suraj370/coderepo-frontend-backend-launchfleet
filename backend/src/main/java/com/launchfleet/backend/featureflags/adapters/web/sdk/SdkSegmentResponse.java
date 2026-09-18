package com.launchfleet.backend.featureflags.adapters.web.sdk;

import java.util.List;

/** Referenced by id from a rule's SEGMENT_MATCH condition values - never embedded per rule. */
public record SdkSegmentResponse(String id, String key, List<SdkConditionResponse> conditions) {
}
