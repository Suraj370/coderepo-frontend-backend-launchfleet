package com.launchfleet.sdk.transport;

/**
 * The outcome of one ConfigurationFetcher.fetch call - deliberately not an exception-
 * based API, since "server unavailable" and "not modified" are expected, routine
 * outcomes for the caller (ConfigurationCache), not exceptional ones.
 */
public sealed interface FetchResult {

	/** A newer configuration was returned (HTTP 200). etag is null if the server omitted it. */
	record Updated(SdkConfigurationWire payload, String etag) implements FetchResult {
	}

	/** The caller's If-None-Match matched (HTTP 304) - its existing snapshot is still current. */
	record NotModified() implements FetchResult {
	}

	/** The request failed for any reason (network, timeout, non-2xx/304/401/403 status, malformed body). */
	record Failed(String reason, Throwable cause) implements FetchResult {
	}
}
