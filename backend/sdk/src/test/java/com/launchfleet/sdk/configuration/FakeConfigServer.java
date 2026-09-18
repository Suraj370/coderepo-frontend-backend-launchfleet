package com.launchfleet.sdk.configuration;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A minimal, in-process stand-in for GET /api/v1/sdk/config - the JDK's own
 * com.sun.net.httpserver.HttpServer, not a new test dependency. Lets ConfigurationCache/
 * ConfigurationFetcher be exercised against real HTTP (real conditional-GET headers,
 * real timeouts, real malformed bodies) without needing the Spring Boot backend or
 * MongoDB running.
 */
final class FakeConfigServer implements AutoCloseable {

	enum Mode {
		OK, SLOW_OK, SERVER_ERROR, MALFORMED_JSON, RICH_OK
	}

	private final HttpServer server;

	private final AtomicInteger requestCount = new AtomicInteger();

	private volatile Mode mode = Mode.OK;

	private volatile String version = "v1";

	private volatile long slowDelayMillis = 0;

	FakeConfigServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/v1/sdk/config", this::handle);
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
	}

	String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	int requestCount() {
		return requestCount.get();
	}

	void setVersion(String version) {
		this.version = version;
	}

	void setMode(Mode mode) {
		this.mode = mode;
	}

	void setSlowDelayMillis(long millis) {
		this.slowDelayMillis = millis;
	}

	private void handle(HttpExchange exchange) throws IOException {
		requestCount.incrementAndGet();

		if (mode == Mode.SLOW_OK && slowDelayMillis > 0) {
			try {
				Thread.sleep(slowDelayMillis);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		}

		if (mode == Mode.SERVER_ERROR) {
			exchange.sendResponseHeaders(500, -1);
			exchange.close();
			return;
		}

		String etag = "\"" + version + "\"";
		String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
		if (etag.equals(ifNoneMatch)) {
			exchange.getResponseHeaders().add("ETag", etag);
			exchange.sendResponseHeaders(304, -1);
			exchange.close();
			return;
		}

		String body = mode == Mode.MALFORMED_JSON ? "{not-valid-json" : mode == Mode.RICH_OK ? richOkBody() : okBody();
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.getResponseHeaders().add("ETag", etag);
		exchange.sendResponseHeaders(200, bytes.length);
		try (OutputStream responseBody = exchange.getResponseBody()) {
			responseBody.write(bytes);
		}
	}

	private String okBody() {
		return """
				{"environmentId":"env-1","environmentKey":"production","version":"%s","generatedAt":"2024-01-01T00:00:00Z",
				 "flags":[{"key":"checkout","enabled":true,
				    "defaultVariant":{"id":"v-off","key":"false","name":"Off","value":false},
				    "variants":[{"id":"v-off","key":"false","name":"Off","value":false},{"id":"v-on","key":"true","name":"On","value":true}],
				    "targetingRules":[],"rollout":null,"version":1}],
				 "segments":[]}
				""".formatted(version);
	}

	/**
	 * A single flag deliberately exercising every evaluation branch (ATTRIBUTE targeting,
	 * SEGMENT_MATCH targeting, rollout fallback, default) in one payload - used by
	 * NoNetworkDuringEvaluationTest, which needs a real fetched snapshot rich enough that
	 * looping over varied contexts actually walks all of FlagEvaluator's branches, not
	 * just the disabled/default one. No other test opts into this mode, so existing
	 * Mode.OK-based tests are unaffected by this payload's shape.
	 */
	private String richOkBody() {
		return """
				{"environmentId":"env-1","environmentKey":"production","version":"%s","generatedAt":"2024-01-01T00:00:00Z",
				 "flags":[{"key":"checkout","enabled":true,
				    "defaultVariant":{"id":"v-off","key":"off","name":"Off","value":false},
				    "variants":[
				      {"id":"v-off","key":"off","name":"Off","value":false},
				      {"id":"v-gold","key":"gold","name":"Gold","value":"gold"},
				      {"id":"v-segment","key":"segment","name":"Segment","value":"segment"},
				      {"id":"v-rollout-a","key":"rollout-a","name":"Rollout A","value":"rollout-a"},
				      {"id":"v-rollout-b","key":"rollout-b","name":"Rollout B","value":"rollout-b"}],
				    "targetingRules":[
				      {"id":"r-attribute","priority":0,"variantId":"v-gold",
				        "conditions":[{"type":"ATTRIBUTE","attribute":"plan","operator":"EQUALS","values":["gold"]}]},
				      {"id":"r-segment","priority":1,"variantId":"v-segment",
				        "conditions":[{"type":"SEGMENT_MATCH","attribute":null,"operator":"EQUALS","values":["seg-1"]}]}],
				    "rollout":{"allocations":[{"variantId":"v-rollout-a","percentage":5000},{"variantId":"v-rollout-b","percentage":5000}]},
				    "version":1}],
				 "segments":[{"id":"seg-1","key":"beta","conditions":[{"type":"USER_KEY","attribute":null,"operator":"IN","values":["dave","erin"]}]}]}
				""".formatted(version);
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
