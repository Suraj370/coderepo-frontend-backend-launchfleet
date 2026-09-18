package com.launchfleet.sdk.api;

/**
 * Thrown by getBoolean when the flag's actual value isn't a Boolean - an actual
 * programmer/API misuse (calling the wrong convenience method for a flag's type), not a
 * runtime configuration condition. Every other outcome (flag missing, no configuration
 * yet, disabled, etc.) returns the caller's fallback instead of throwing - see
 * LaunchFleetClient.
 */
public class SdkEvaluationTypeException extends RuntimeException {

	public SdkEvaluationTypeException(String flagKey, String expectedType, Object actualValue) {
		super("Flag '" + flagKey + "' does not have a " + expectedType + " value (actual type: "
				+ (actualValue == null ? "null" : actualValue.getClass().getSimpleName()) + ").");
	}
}
