package com.launchfleet.sdk.api;

/**
 * The SDK's entire observability surface for failures (initialization/refresh) -
 * intentionally just a callback, not a metrics/tracing/event-pipeline system. Invoked
 * from the background refresh thread; implementations must not block or throw (a
 * throwing listener is caught and ignored by ConfigurationCache so a broken listener
 * can never take down refresh). Never receives userKey/attributes/the SDK credential -
 * only a human-readable message and, where available, the underlying cause.
 */
@FunctionalInterface
public interface SdkErrorListener {

	void onError(String message, Throwable cause);

}
