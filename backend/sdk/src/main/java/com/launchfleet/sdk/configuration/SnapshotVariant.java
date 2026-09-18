package com.launchfleet.sdk.configuration;

/** value is intentionally untyped, mirroring the backend Variant - a boolean or an arbitrary JSON-decoded payload. */
public record SnapshotVariant(String id, String key, String name, Object value) {
}
