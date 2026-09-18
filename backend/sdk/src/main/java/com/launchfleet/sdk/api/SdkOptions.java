package com.launchfleet.sdk.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Caller-configurable knobs, all with sane defaults - deliberately just four fields,
 * not a general options bag. MIN_REFRESH_INTERVAL exists so a caller can't accidentally
 * configure a sub-floor polling interval that hammers the configuration endpoint.
 */
public final class SdkOptions {

	public static final Duration MIN_REFRESH_INTERVAL = Duration.ofSeconds(5);

	public static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofSeconds(30);

	public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

	public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);

	public static final Duration DEFAULT_INITIAL_FETCH_TIMEOUT = Duration.ofSeconds(5);

	private final Duration refreshInterval;

	private final Duration connectTimeout;

	private final Duration requestTimeout;

	private final Duration initialFetchTimeout;

	private final SdkErrorListener errorListener;

	private SdkOptions(Duration refreshInterval, Duration connectTimeout, Duration requestTimeout,
			Duration initialFetchTimeout, SdkErrorListener errorListener) {
		this.refreshInterval = refreshInterval;
		this.connectTimeout = connectTimeout;
		this.requestTimeout = requestTimeout;
		this.initialFetchTimeout = initialFetchTimeout;
		this.errorListener = errorListener;
	}

	public static SdkOptions defaults() {
		return new SdkOptions(DEFAULT_REFRESH_INTERVAL, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT,
				DEFAULT_INITIAL_FETCH_TIMEOUT, null);
	}

	public SdkOptions withRefreshInterval(Duration refreshInterval) {
		Objects.requireNonNull(refreshInterval, "refreshInterval is required.");
		if (refreshInterval.compareTo(MIN_REFRESH_INTERVAL) < 0) {
			throw new IllegalArgumentException("refreshInterval must be at least " + MIN_REFRESH_INTERVAL + ".");
		}

		return new SdkOptions(refreshInterval, connectTimeout, requestTimeout, initialFetchTimeout, errorListener);
	}

	public SdkOptions withConnectTimeout(Duration connectTimeout) {
		Objects.requireNonNull(connectTimeout, "connectTimeout is required.");

		return new SdkOptions(refreshInterval, connectTimeout, requestTimeout, initialFetchTimeout, errorListener);
	}

	public SdkOptions withRequestTimeout(Duration requestTimeout) {
		Objects.requireNonNull(requestTimeout, "requestTimeout is required.");

		return new SdkOptions(refreshInterval, connectTimeout, requestTimeout, initialFetchTimeout, errorListener);
	}

	public SdkOptions withInitialFetchTimeout(Duration initialFetchTimeout) {
		Objects.requireNonNull(initialFetchTimeout, "initialFetchTimeout is required.");

		return new SdkOptions(refreshInterval, connectTimeout, requestTimeout, initialFetchTimeout, errorListener);
	}

	public SdkOptions withErrorListener(SdkErrorListener errorListener) {
		return new SdkOptions(refreshInterval, connectTimeout, requestTimeout, initialFetchTimeout, errorListener);
	}

	public Duration refreshInterval() {
		return refreshInterval;
	}

	public Duration connectTimeout() {
		return connectTimeout;
	}

	public Duration requestTimeout() {
		return requestTimeout;
	}

	public Duration initialFetchTimeout() {
		return initialFetchTimeout;
	}

	public SdkErrorListener errorListener() {
		return errorListener;
	}
}
