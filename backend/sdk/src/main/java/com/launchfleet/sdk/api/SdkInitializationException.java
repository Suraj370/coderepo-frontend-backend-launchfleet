package com.launchfleet.sdk.api;

/**
 * Thrown by LaunchFleetClient.initialize (blocking) when the initial configuration
 * fetch does not succeed within the configured timeout - the SDK never fabricates a
 * configuration to paper over this. Not thrown by initializeAsync, which returns
 * immediately by design and serves fallbacks until the first fetch completes.
 */
public class SdkInitializationException extends Exception {

	public SdkInitializationException(String message) {
		super(message);
	}

	public SdkInitializationException(String message, Throwable cause) {
		super(message, cause);
	}
}
