package com.launchfleet.sdk.transport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The only component that touches the network or knows the SDK credential. Pure
 * transport: builds the authenticated/conditional request, sends it, classifies the
 * response, and parses a 200 body into the wire DTO - nothing more. It does not
 * evaluate flags, does not hold the configuration cache/snapshot, and has no
 * MongoDB/Spring dependency of any kind (see the sdk module's build.gradle).
 *
 * Never logs the bearer credential, and the credential never appears in a thrown
 * exception's message either - failures are reported as a FetchResult.Failed reason
 * string built from the HTTP status/exception message only.
 */
public final class ConfigurationFetcher implements AutoCloseable {

	private static final String CONFIG_PATH = "/api/v1/sdk/config";

	private static final int HTTP_OK = 200;

	private static final int HTTP_NOT_MODIFIED = 304;

	private static final int HTTP_UNAUTHORIZED = 401;

	private static final int HTTP_FORBIDDEN = 403;

	private final HttpClient httpClient;

	private final URI configurationUri;

	private final String sdkKey;

	private final Duration requestTimeout;

	private final ObjectMapper objectMapper;

	public ConfigurationFetcher(String baseUrl, String sdkKey, Duration connectTimeout, Duration requestTimeout) {
		Objects.requireNonNull(baseUrl, "baseUrl is required.");
		this.sdkKey = Objects.requireNonNull(sdkKey, "sdkKey is required.");
		this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout is required.");
		this.configurationUri = URI.create(stripTrailingSlash(baseUrl) + CONFIG_PATH);
		this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
		this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	/** previousEtag may be null (no prior snapshot yet, or the server never returned one). */
	public FetchResult fetch(String previousEtag) {
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(configurationUri).GET()
				.timeout(requestTimeout)
				.header("Authorization", "Bearer " + sdkKey)
				.header("Accept", "application/json");

		if (previousEtag != null && !previousEtag.isBlank()) {
			requestBuilder.header("If-None-Match", previousEtag);
		}

		HttpResponse<String> response;
		try {
			response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
		} catch (IOException exception) {
			return new FetchResult.Failed("Configuration request failed: " + exception.getMessage(), exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return new FetchResult.Failed("Configuration request was interrupted.", exception);
		}

		return classify(response);
	}

	private FetchResult classify(HttpResponse<String> response) {
		int status = response.statusCode();

		if (status == HTTP_NOT_MODIFIED) {
			return new FetchResult.NotModified();
		}

		if (status == HTTP_OK) {
			try {
				SdkConfigurationWire payload = objectMapper.readValue(response.body(), SdkConfigurationWire.class);
				String etag = response.headers().firstValue("ETag").orElse(null);

				return new FetchResult.Updated(payload, etag);
			} catch (IOException exception) {
				return new FetchResult.Failed("Configuration response could not be parsed: " + exception.getMessage(),
						exception);
			}
		}

		if (status == HTTP_UNAUTHORIZED || status == HTTP_FORBIDDEN) {
			return new FetchResult.Failed("Configuration request was not authorized (HTTP " + status + ").", null);
		}

		return new FetchResult.Failed("Configuration request returned an unexpected HTTP status: " + status, null);
	}

	@Override
	public void close() {
		httpClient.close();
	}

	private static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
