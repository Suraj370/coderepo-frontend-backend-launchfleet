package com.launchfleet.sdk.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.launchfleet.sdk.api.EvaluationContext;
import com.launchfleet.sdk.api.EvaluationDetail;
import com.launchfleet.sdk.api.EvaluationReason;
import com.launchfleet.sdk.api.LaunchFleetClient;
import com.launchfleet.sdk.api.SdkOptions;

/**
 * The regression this SDK's whole caching design exists to satisfy: once a
 * configuration has been fetched, evaluation must be pure local computation - no
 * matter how many evaluations run, or which branch (targeting/segment/rollout/default)
 * each one takes, the transport layer sees no additional requests. Proven end to end
 * through the public LaunchFleetClient facade against a real (fake) HTTP server, so a
 * regression that accidentally made getBoolean/getVariant/evaluateDetail reach back out
 * to ConfigurationFetcher would show up as a growing request count here, not just as a
 * suspicious method call found by code review.
 */
class NoNetworkDuringEvaluationTest {

	private FakeConfigServer server;

	@BeforeEach
	void startServer() throws Exception {
		server = new FakeConfigServer();
		server.setMode(FakeConfigServer.Mode.RICH_OK);
	}

	@AfterEach
	void stopServer() {
		server.close();
	}

	@Test
	void manyEvaluationsAcrossEveryBranchNeverIncreaseTheTransportRequestCount() throws Exception {
		// A refresh interval far longer than this test can possibly run, so a background
		// scheduled refresh can't sneak in and confound the request-count assertion below.
		SdkOptions options = SdkOptions.defaults().withRefreshInterval(Duration.ofMinutes(10));

		try (LaunchFleetClient client = LaunchFleetClient.initialize("test-sdk-key", server.baseUrl(), options)) {
			int requestsAfterInit = server.requestCount();
			assertThat(requestsAfterInit).isEqualTo(1);
			assertThat(client.isInitialized()).isTrue();

			// One context per FlagEvaluator branch this flag is built to exercise (see
			// FakeConfigServer.richOkBody): NO_USER_KEY/default, ATTRIBUTE targeting, segment
			// targeting, and rollout fallback.
			EvaluationContext noUserKey = EvaluationContext.of(null);
			EvaluationContext attributeMatch = EvaluationContext.of("alice", Map.of("plan", "gold"));
			EvaluationContext segmentMatch = EvaluationContext.of("dave");
			EvaluationContext rolloutFallback = EvaluationContext.of("frank");
			List<EvaluationContext> contexts = List.of(noUserKey, attributeMatch, segmentMatch, rolloutFallback);

			for (int i = 0; i < 10_000; i++) {
				EvaluationContext context = contexts.get(i % contexts.size());

				boolean defaultResult = client.getBoolean("checkout", noUserKey, false);
				String variantResult = client.getVariant("checkout", context, "fallback");
				EvaluationDetail detail = client.evaluateDetail("checkout", context);

				assertThat(defaultResult).isFalse();
				assertThat(variantResult).isNotNull();
				assertThat(detail.hasVariant()).isTrue();
			}

			// The branch assertions below aren't the point of this test (FlagEvaluatorTest
			// already covers correctness) - they're here so a future change that collapsed
			// every context onto the same branch (defeating the "every branch" premise above)
			// would itself fail loudly, rather than this test passing for the wrong reason.
			assertThat(client.evaluateDetail("checkout", noUserKey).reason()).isEqualTo(EvaluationReason.NO_USER_KEY);
			assertThat(client.evaluateDetail("checkout", attributeMatch).reason()).isEqualTo(EvaluationReason.TARGET_MATCH);
			assertThat(client.evaluateDetail("checkout", segmentMatch).reason()).isEqualTo(EvaluationReason.TARGET_MATCH);
			assertThat(client.evaluateDetail("checkout", rolloutFallback).reason()).isEqualTo(EvaluationReason.ROLLOUT);

			assertThat(server.requestCount()).isEqualTo(requestsAfterInit);
		}
	}

	@Test
	void aCallerTriggeredRefreshIsTheOnlyThingThatAddsARequestNotEvaluation() throws Exception {
		SdkOptions options = SdkOptions.defaults().withRefreshInterval(Duration.ofMinutes(10));

		try (LaunchFleetClient client = LaunchFleetClient.initialize("test-sdk-key", server.baseUrl(), options)) {
			int requestsAfterInit = server.requestCount();

			for (int i = 0; i < 500; i++) {
				client.getBoolean("checkout", EvaluationContext.of(null), false);
			}
			assertThat(server.requestCount()).isEqualTo(requestsAfterInit);

			// An explicit, caller-initiated refresh() - never evaluation - is the only thing
			// that legitimately adds a request.
			client.refresh();
			assertThat(server.requestCount()).isEqualTo(requestsAfterInit + 1);
		}
	}
}
