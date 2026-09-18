package com.launchfleet.backend.featureflags.adapters.web.sdk;

import java.util.List;

public record SdkRolloutResponse(List<SdkAllocationResponse> allocations) {
}
