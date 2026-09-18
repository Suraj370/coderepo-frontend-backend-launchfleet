package com.launchfleet.sdk.configuration;

/**
 * Thrown by ConfigurationSnapshot.fromWire when a server response is structurally
 * unusable (missing required fields, a default variant that doesn't exist, etc.).
 * ConfigurationCache catches this and keeps the previous snapshot active - a malformed
 * response must never replace a known-good configuration, let alone crash the caller.
 */
public class InvalidConfigurationException extends RuntimeException {

	public InvalidConfigurationException(String message) {
		super(message);
	}
}
