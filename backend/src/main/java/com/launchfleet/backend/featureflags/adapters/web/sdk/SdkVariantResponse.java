package com.launchfleet.backend.featureflags.adapters.web.sdk;

/** value is intentionally untyped, mirroring Variant.value - a boolean or an arbitrary JSON payload. */
public record SdkVariantResponse(String id, String key, String name, Object value) {
}
