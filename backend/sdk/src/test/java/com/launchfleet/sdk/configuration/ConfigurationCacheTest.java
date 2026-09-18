package com.launchfleet.sdk.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.launchfleet.sdk.api.EvaluationContext;
import com.launchfleet.sdk.api.SdkInitializationException;
import com.launchfleet.sdk.evaluation.FlagEvaluator;
import com.launchfleet.sdk.transport.ConfigurationFetcher;

/**
 * Exercises ConfigurationCache end to end against a real (fake) HTTP server: initial
 * fetch (success/failure/timeout), non-blocking init, manual refresh, 304 handling,
 * failed/malformed refresh retaining the previous snapshot, concurrent refresh
 * protection, evaluation-during-refresh, and close() stopping the scheduler.
 */
class ConfigurationCacheTest {

	private FakeConfigServer server;

	@BeforeEach
	void startServer() throws Exception {
		server = new FakeConfigServer();
	}

	@AfterEach
	void stopServer() {
		server.close();
	}

	@Test
	void initialFetchSucceedsAndPopulatesTheSnapshot() throws Exception {
		ConfigurationCache cache = newCache();
		try {
			cache.initializeBlocking();

			assertThat(cache.currentSnapshot()).isNotNull();
			assertThat(cache.currentSnapshot().version()).isEqualTo("v1");
		} finally {
			cache.close();
		}
	}

	@Test
	void initialFetchFailureFailsBlockingInitialization() {
		server.setMode(FakeConfigServer.Mode.SERVER_ERROR);
		ConfigurationCache cache = newCache();

		try {
			assertThatThrownBy(cache::initializeBlocking).isInstanceOf(SdkInitializationException.class);
		} finally {
			cache.close();
		}
	}

	@Test
	void blockingInitializationTimesOutRatherThanHangingForever() {
		server.setMode(FakeConfigServer.Mode.SLOW_OK);
		server.setSlowDelayMillis(2000);
		ConfigurationCache cache = new ConfigurationCache(fetcher(), Duration.ofSeconds(30), Duration.ofMillis(200),
				null);

		try {
			assertThatThrownBy(cache::initializeBlocking).isInstanceOf(SdkInitializationException.class);
			assertThat(cache.currentSnapshot()).isNull();
		} finally {
			cache.close();
		}
	}

	@Test
	void blockingInitializationTimeoutInterruptsTheInFlightFetchAndReleasesResources() {
		server.setMode(FakeConfigServer.Mode.SLOW_OK);
		server.setSlowDelayMillis(5000);
		ConfigurationCache cache = new ConfigurationCache(fetcher(), Duration.ofSeconds(30), Duration.ofMillis(200),
				null);

		try {
			long start = System.nanoTime();
			assertThatThrownBy(cache::initializeBlocking).isInstanceOf(SdkInitializationException.class);
			long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

			// Proves the in-flight fetch was actually cancelled/interrupted rather than left
			// running to completion in the background: this returns close to the 200ms
			// timeout, nowhere near the server's 5-second delay. Before the fix this used a
			// CompletableFuture whose cancel(true) never interrupts the underlying thread, so
			// only the caller's wait was bounded - the fetch itself kept running for the full
			// 5 seconds regardless.
			assertThat(elapsedMillis).isLessThan(1500);

			// Proves close() actually ran as part of the failed initialization (not left for
			// a caller who never received a LaunchFleetClient to call it on): the fetcher's
			// HttpClient is already closed, so a caller-triggered refresh on this now-unusable
			// cache fails rather than quietly succeeding against a lingering connection.
			assertThat(cache.refresh()).isFalse();
		} finally {
			cache.close();
		}
	}

	@Test
	void nonBlockingInitializationReturnsImmediatelyAndPopulatesLater() throws Exception {
		ConfigurationCache cache = newCache();
		try {
			long start = System.nanoTime();
			cache.initializeAsync();
			long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

			assertThat(elapsedMillis).isLessThan(200);
			assertThat(cache.currentSnapshot()).isNull();

			awaitUntil(Duration.ofSeconds(2), () -> cache.currentSnapshot() != null);
		} finally {
			cache.close();
		}
	}

	@Test
	void manualRefreshPicksUpANewVersion() throws Exception {
		ConfigurationCache cache = newCache();
		try {
			cache.initializeBlocking();
			assertThat(cache.currentSnapshot().version()).isEqualTo("v1");

			server.setVersion("v2");
			boolean succeeded = cache.refresh();

			assertThat(succeeded).isTrue();
			assertThat(cache.currentSnapshot().version()).isEqualTo("v2");
		} finally {
			cache.close();
		}
	}

	@Test
	void unchangedConfigurationReturns304AndKeepsTheSameSnapshotVersion() throws Exception {
		ConfigurationCache cache = newCache();
		try {
			cache.initializeBlocking();
			ConfigurationSnapshot firstSnapshot = cache.currentSnapshot();

			boolean succeeded = cache.refresh();

			assertThat(succeeded).isTrue();
			assertThat(cache.currentSnapshot()).isSameAs(firstSnapshot);
		} finally {
			cache.close();
		}
	}

	@Test
	void aFailedRefreshRetainsThePreviousSnapshotAndReportsAnError() throws Exception {
		AtomicReference<String> lastError = new AtomicReference<>();
		ConfigurationCache cache = new ConfigurationCache(fetcher(), Duration.ofSeconds(30), Duration.ofSeconds(5),
				(message, cause) -> lastError.set(message));
		try {
			cache.initializeBlocking();
			ConfigurationSnapshot goodSnapshot = cache.currentSnapshot();

			server.setMode(FakeConfigServer.Mode.SERVER_ERROR);
			boolean succeeded = cache.refresh();

			assertThat(succeeded).isFalse();
			assertThat(cache.currentSnapshot()).isSameAs(goodSnapshot);
			assertThat(lastError.get()).isNotNull();
		} finally {
			cache.close();
		}
	}

	@Test
	void aMalformedResponseRetainsThePreviousSnapshotRatherThanCrashing() throws Exception {
		ConfigurationCache cache = newCache();
		try {
			cache.initializeBlocking();
			ConfigurationSnapshot goodSnapshot = cache.currentSnapshot();

			// A different version so the fake server doesn't short-circuit to 304 before
			// this test can exercise the malformed-body path at all.
			server.setVersion("v-malformed");
			server.setMode(FakeConfigServer.Mode.MALFORMED_JSON);
			boolean succeeded = cache.refresh();

			assertThat(succeeded).isFalse();
			assertThat(cache.currentSnapshot()).isSameAs(goodSnapshot);
		} finally {
			cache.close();
		}
	}

	@Test
	void concurrentManualRefreshesResultInAtMostOneInFlightFetch() throws Exception {
		ConfigurationCache cache = newCache();
		try {
			cache.initializeBlocking();
			server.setMode(FakeConfigServer.Mode.SLOW_OK);
			server.setSlowDelayMillis(300);
			server.setVersion("v2");

			int requestsBefore = server.requestCount();
			CountDownLatch ready = new CountDownLatch(2);
			CountDownLatch go = new CountDownLatch(1);
			AtomicInteger successes = new AtomicInteger();

			Runnable task = () -> {
				ready.countDown();
				try {
					go.await();
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				}
				if (cache.refresh()) {
					successes.incrementAndGet();
				}
			};
			Thread t1 = new Thread(task);
			Thread t2 = new Thread(task);
			t1.start();
			t2.start();
			ready.await();
			go.countDown();
			t1.join();
			t2.join();

			// At most one of the two overlapping calls actually performed a fetch - the
			// other is a same-turn no-op per the single-in-flight guard - so the server sees
			// at most one additional request, not two.
			assertThat(server.requestCount() - requestsBefore).isLessThanOrEqualTo(1);
			assertThat(successes.get()).isEqualTo(2);
		} finally {
			cache.close();
		}
	}

	@Test
	void evaluationDuringRefreshNeverThrowsAndAlwaysSeesAWholeSnapshot() throws Exception {
		ConfigurationCache cache = newCache();
		try {
			cache.initializeBlocking();

			AtomicReference<Throwable> evaluatorFailure = new AtomicReference<>();
			Thread evaluator = new Thread(() -> {
				EvaluationContext context = EvaluationContext.of("alice");
				for (int i = 0; i < 2000; i++) {
					try {
						ConfigurationSnapshot snapshot = cache.currentSnapshot();
						if (snapshot != null) {
							FlagEvaluator.evaluate(snapshot, "checkout", context);
						}
					} catch (Exception exception) {
						evaluatorFailure.set(exception);
						return;
					}
				}
			});
			evaluator.start();

			for (int i = 0; i < 20; i++) {
				server.setVersion("v" + i);
				cache.refresh();
			}

			evaluator.join();

			assertThat(evaluatorFailure.get()).isNull();
		} finally {
			cache.close();
		}
	}

	@Test
	void consecutiveFailuresIncrementOnEachFailedRefresh() throws Exception {
		ConfigurationCache cache = newCache();
		try {
			cache.initializeBlocking();
			assertThat(cache.consecutiveFailures()).isZero();

			server.setMode(FakeConfigServer.Mode.SERVER_ERROR);
			cache.refresh();
			assertThat(cache.consecutiveFailures()).isEqualTo(1);

			cache.refresh();
			assertThat(cache.consecutiveFailures()).isEqualTo(2);

			cache.refresh();
			assertThat(cache.consecutiveFailures()).isEqualTo(3);
		} finally {
			cache.close();
		}
	}

	@Test
	void aSuccessfulRefreshResetsConsecutiveFailuresAndTheNextFailureStartsOverAtOne() throws Exception {
		ConfigurationCache cache = newCache();
		try {
			cache.initializeBlocking();

			server.setMode(FakeConfigServer.Mode.SERVER_ERROR);
			cache.refresh();
			cache.refresh();
			assertThat(cache.consecutiveFailures()).isEqualTo(2);

			// A success (a genuinely new version, so the fake server doesn't just serve a
			// 304 - either outcome resets the counter, but this exercises the Updated path)
			// resets the counter back to zero...
			server.setMode(FakeConfigServer.Mode.OK);
			server.setVersion("v-recovered");
			boolean succeeded = cache.refresh();

			assertThat(succeeded).isTrue();
			assertThat(cache.consecutiveFailures()).isZero();

			// ...so a subsequent failure starts the backoff calculation over from
			// consecutiveFailures=1 (BackoffPolicy's initial range - see BackoffPolicyTest)
			// rather than continuing to climb from where it left off before the recovery.
			server.setMode(FakeConfigServer.Mode.SERVER_ERROR);
			cache.refresh();

			assertThat(cache.consecutiveFailures()).isEqualTo(1);
		} finally {
			cache.close();
		}
	}

	@Test
	void aNotModifiedRefreshAlsoResetsConsecutiveFailures() throws Exception {
		ConfigurationCache cache = newCache();
		try {
			cache.initializeBlocking();

			server.setMode(FakeConfigServer.Mode.SERVER_ERROR);
			cache.refresh();
			assertThat(cache.consecutiveFailures()).isEqualTo(1);

			// Same version as the initial fetch -> 304 Not Modified, not a fresh Updated -
			// still counts as "no worse off than before" and resets the counter.
			server.setMode(FakeConfigServer.Mode.OK);
			boolean succeeded = cache.refresh();

			assertThat(succeeded).isTrue();
			assertThat(cache.consecutiveFailures()).isZero();
		} finally {
			cache.close();
		}
	}

	@Test
	void concurrentRefreshesThatAllFailStillResultInAtMostOneInFlightFetch() throws Exception {
		server.setMode(FakeConfigServer.Mode.SLOW_OK);
		server.setSlowDelayMillis(300);
		// A short client-side request timeout races the server's artificial delay, so
		// every overlapping refresh() call actually fails rather than slowly succeeding.
		ConfigurationCache cache = new ConfigurationCache(
				new ConfigurationFetcher(server.baseUrl(), "test-sdk-key", Duration.ofSeconds(2), Duration.ofMillis(50)),
				Duration.ofSeconds(30), Duration.ofSeconds(5), null);
		try {
			int requestsBefore = server.requestCount();
			CountDownLatch ready = new CountDownLatch(2);
			CountDownLatch go = new CountDownLatch(1);

			Runnable task = () -> {
				ready.countDown();
				try {
					go.await();
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				}
				cache.refresh();
			};
			Thread t1 = new Thread(task);
			Thread t2 = new Thread(task);
			t1.start();
			t2.start();
			ready.await();
			go.countDown();
			t1.join();
			t2.join();

			// The single-in-flight guard (refreshInFlight, compare-and-set) applies before
			// success/failure is known, so two overlapping calls - even ones that end up
			// failing - still produce at most one additional request, and the failure count
			// reflects at most one observed failure, not two independent ones.
			assertThat(server.requestCount() - requestsBefore).isLessThanOrEqualTo(1);
			assertThat(cache.consecutiveFailures()).isLessThanOrEqualTo(1);
		} finally {
			cache.close();
		}
	}

	@Test
	void closeStopsScheduledRefreshActivity() throws Exception {
		ConfigurationCache cache = new ConfigurationCache(fetcher(), Duration.ofMillis(100), Duration.ofSeconds(5),
				null);
		try {
			cache.initializeBlocking();
			awaitUntil(Duration.ofSeconds(2), () -> server.requestCount() >= 2);
		} finally {
			cache.close();
		}

		int countAtClose = server.requestCount();
		Thread.sleep(400);

		assertThat(server.requestCount()).isEqualTo(countAtClose);
	}

	private ConfigurationCache newCache() {
		return new ConfigurationCache(fetcher(), Duration.ofSeconds(30), Duration.ofSeconds(5), null);
	}

	private ConfigurationFetcher fetcher() {
		return new ConfigurationFetcher(server.baseUrl(), "test-sdk-key", Duration.ofSeconds(2), Duration.ofSeconds(2));
	}

	/** A tiny manual poll loop - deliberately not adding a test dependency (e.g. Awaitility) just for this. */
	private static void awaitUntil(Duration timeout, java.util.function.BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("Condition was not met within " + timeout);
			}
			Thread.sleep(20);
		}
	}
}
