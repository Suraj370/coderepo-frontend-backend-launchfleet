package com.launchfleet.backend.featureflags.ports;

/**
 * Just enough of a Project for this module to resolve/scope by - not the real
 * Project entity (which lives in the projects module and carries Mongo mapping
 * concerns this module has no business depending on).
 */
public record ProjectRef(String id, String key) {
}
