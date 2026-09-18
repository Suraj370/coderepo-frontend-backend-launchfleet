package com.launchfleet.sdk.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.launchfleet.sdk.configuration.ConfigurationCache;
import com.launchfleet.sdk.configuration.ConfigurationSnapshot;
import com.launchfleet.sdk.evaluation.FlagEvaluator;
import com.launchfleet.sdk.transport.ConfigurationFetcher;

/**
 * The SDK's public facade - the only class most callers ever touch directly. Owns no
 * evaluation/caching/networking logic itself; it composes ConfigurationCache (lifecycle
 * + snapshot) and FlagEvaluator (pure evaluation) and exposes a small, idiomatic Java
 * API on top. getBoolean/getVariant never perform network I/O - they read whatever
 * snapshot the cache currently holds.
 *
 * Usage:
 * <pre>{@code
 * try (LaunchFleetClient client = LaunchFleetClient.initialize(sdkKey, baseUrl)) {
 *     EvaluationContext context = EvaluationContext.of(userKey, Map.of("plan", "gold"));
 *     boolean enabled = client.getBoolean("new-checkout", context, false);
 * }
 * }</pre>
 */
public final class LaunchFleetClient implements AutoCloseable {

	private final ConfigurationCache cache;

	private LaunchFleetClient(ConfigurationCache cache) {
		this.cache = cache;
	}

	/** Blocking (default): fetches the initial configuration before returning, up to SdkOptions.initialFetchTimeout. */
	public static LaunchFleetClient initialize(String sdkKey, String baseUrl) throws SdkInitializationException {
		return initialize(sdkKey, baseUrl, SdkOptions.defaults());
	}

	/** Blocking (default): fails with SdkInitializationException rather than fabricating a configuration. */
	public static LaunchFleetClient initialize(String sdkKey, String baseUrl, SdkOptions options)
			throws SdkInitializationException {
		ConfigurationCache cache = newCache(sdkKey, baseUrl, options);
		cache.initializeBlocking();

		return new LaunchFleetClient(cache);
	}

	/**
	 * Non-blocking (explicit opt-in, never the default): returns immediately. Until the
	 * first background fetch completes, evaluations report NO_CONFIGURATION and
	 * convenience methods return the caller's fallback.
	 */
	public static LaunchFleetClient initializeAsync(String sdkKey, String baseUrl) {
		return initializeAsync(sdkKey, baseUrl, SdkOptions.defaults());
	}

	public static LaunchFleetClient initializeAsync(String sdkKey, String baseUrl, SdkOptions options) {
		ConfigurationCache cache = newCache(sdkKey, baseUrl, options);
		cache.initializeAsync();

		return new LaunchFleetClient(cache);
	}

	private static ConfigurationCache newCache(String sdkKey, String baseUrl, SdkOptions options) {
		Objects.requireNonNull(sdkKey, "sdkKey is required.");
		Objects.requireNonNull(baseUrl, "baseUrl is required.");
		Objects.requireNonNull(options, "options is required.");

		ConfigurationFetcher fetcher = new ConfigurationFetcher(baseUrl, sdkKey, options.connectTimeout(),
				options.requestTimeout());

		return new ConfigurationCache(fetcher, options.refreshInterval(), options.initialFetchTimeout(),
				options.errorListener());
	}

	/**
	 * No network I/O. Returns fallback whenever the flag can't be resolved to a real
	 * variant (not found, no configuration yet). Throws SdkEvaluationTypeException if
	 * the flag resolves but its value isn't a Boolean - that's caller misuse (wrong
	 * convenience method for this flag's type), not a runtime condition to fall back on.
	 */
	public boolean getBoolean(String flagKey, EvaluationContext context, boolean fallback) {
		EvaluationDetail detail = evaluateDetail(flagKey, context);
		if (!detail.hasVariant()) {
			return fallback;
		}
		if (detail.value() instanceof Boolean value) {
			return value;
		}

		throw new SdkEvaluationTypeException(flagKey, "boolean", detail.value());
	}

	/** No network I/O. Returns fallbackVariantKey whenever the flag can't be resolved to a real variant. */
	public String getVariant(String flagKey, EvaluationContext context, String fallbackVariantKey) {
		EvaluationDetail detail = evaluateDetail(flagKey, context);

		return detail.hasVariant() ? detail.variantKey() : fallbackVariantKey;
	}

	/** The full structured result, for callers that want the reason/configuration version rather than just a value. */
	public EvaluationDetail evaluateDetail(String flagKey, EvaluationContext context) {
		Objects.requireNonNull(flagKey, "flagKey is required.");
		Objects.requireNonNull(context, "context is required.");

		ConfigurationSnapshot snapshot = cache.currentSnapshot();
		if (snapshot == null) {
			return EvaluationDetail.noConfiguration();
		}

		return FlagEvaluator.evaluate(snapshot, flagKey, context);
	}

	/** A caller-triggered refresh, sharing the same in-flight/backoff machinery as the background scheduler. Blocks the caller. */
	public boolean refresh() {
		return cache.refresh();
	}

	public boolean isInitialized() {
		return cache.currentSnapshot() != null;
	}

	public Optional<String> configurationVersion() {
		ConfigurationSnapshot snapshot = cache.currentSnapshot();

		return Optional.ofNullable(snapshot).map(ConfigurationSnapshot::version);
	}

	public Optional<Duration> configurationAge() {
		ConfigurationSnapshot snapshot = cache.currentSnapshot();

		return Optional.ofNullable(snapshot).map(current -> current.age(Instant.now()));
	}

	/** Stops the background refresh scheduler and releases the HTTP client. No persisted state to flush. */
	@Override
	public void close() {
		cache.close();
	}
}
