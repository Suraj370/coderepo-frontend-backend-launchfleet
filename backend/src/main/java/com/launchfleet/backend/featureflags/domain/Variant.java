package com.launchfleet.backend.featureflags.domain;

/**
 * An immutable value object embedded within FeatureFlag - a variant has no identity
 * or query pattern outside its owning flag. value is intentionally untyped (Object) -
 * a plain boolean for BOOLEAN flags, an arbitrary JSON-serializable payload for
 * MULTIVARIANT ones; interpreting it is an evaluation-engine concern (future work).
 */
public record Variant(String id, String key, String name, Object value, int order) {
}
