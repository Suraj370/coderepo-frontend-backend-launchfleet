package com.launchfleet.sdk.configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.launchfleet.sdk.api.SdkErrorListener;
import com.launchfleet.sdk.api.SdkInitializationException;
import com.launchfleet.sdk.internal.BackoffPolicy;
import com.launchfleet.sdk.transport.ConfigurationFetcher;
import com.launchfleet.sdk.transport.FetchResult;

/**
 * Owns exactly the lifecycle/caching concerns: the current snapshot, the ETag to send
 * next, the background refresh schedule, and failure backoff. It does not evaluate
 * flags (see evaluation.FlagEvaluator) and does not know MongoDB/HTTP details beyond
 * calling ConfigurationFetcher (see transport.ConfigurationFetcher).
 *
 * Concurrency model (see the SDK's thread-safety guarantees):
 *  - the current snapshot lives in a single AtomicReference, replaced only by installing
 *    a whole new, already-validated ConfigurationSnapshot (see doRefresh) - never edited
 *    in place, so a reading thread always sees either the old or the new snapshot, never
 *    a mix;
 *  - refreshInFlight (AtomicBoolean, compare-and-set) ensures at most one fetch/replace
 *    is ever in progress, whether triggered by the scheduler or a caller's manual
 *    refresh() - the simplest correct mechanism for "no concurrent refresh", per the
 *    locked architecture's own instruction to prefer the simplest correct approach;
 *  - evaluation (FlagEvaluator, via currentSnapshot()) never touches the scheduler,
 *    the fetcher, or any lock - a single volatile read of the AtomicReference.
 */
public final class ConfigurationCache implements AutoCloseable {

	private final ConfigurationFetcher fetcher;

	private final Duration refreshInterval;

	private final Duration initialFetchTimeout;

	private final SdkErrorListener errorListener;

	private final AtomicReference<ConfigurationSnapshot> snapshot = new AtomicReference<>();

	private final AtomicReference<String> lastKnownEtag = new AtomicReference<>();

	private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);

	private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

	private final ScheduledExecutorService scheduler;

	private volatile boolean closed = false;

	public ConfigurationCache(ConfigurationFetcher fetcher, Duration refreshInterval, Duration initialFetchTimeout,
			SdkErrorListener errorListener) {
		this.fetcher = fetcher;
		this.refreshInterval = refreshInterval;
		this.initialFetchTimeout = initialFetchTimeout;
		this.errorListener = errorListener == null ? (message, cause) -> {
		} : errorListener;
		this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "launchfleet-sdk-refresh");
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * Performs one fetch on a worker thread, blocking the CALLER up to
	 * initialFetchTimeout. Throws (rather than fabricating an empty snapshot) if that
	 * fetch doesn't succeed in time - the locked "fail closed by default" initialization
	 * behavior. Only on success does it start the periodic background schedule.
	 *
	 * Submitted via the scheduler's own ExecutorService.submit (a real
	 * java.util.concurrent.Future), not CompletableFuture.supplyAsync - a
	 * CompletableFuture's cancel(true) never actually interrupts the thread running its
	 * action (see its javadoc: "the mayInterruptIfRunning parameter has no effect"),
	 * which would leave doRefresh's blocking HTTP call running to completion in the
	 * background even after this method has already thrown. A FutureTask's cancel(true)
	 * does interrupt the worker thread, which ConfigurationFetcher.fetch() already
	 * handles (catches InterruptedException, re-sets the interrupt flag, returns
	 * Failed) - so the in-flight request actually stops.
	 *
	 * On every failure path (timeout, interrupt, execution failure, or a fetch that
	 * simply didn't succeed in time) this also closes the cache before throwing: the
	 * caller is being told initialization failed and, per LaunchFleetClient.initialize,
	 * never receives a LaunchFleetClient to call close() on themselves - so nothing
	 * would otherwise stop this cache's background scheduler thread or release its
	 * HttpClient. Closing here is what "the SDK must not accidentally continue an
	 * initialization lifecycle the caller has already been told failed" means in
	 * practice.
	 */
	public void initializeBlocking() throws SdkInitializationException {
		Future<Boolean> initialFetch = scheduler.submit(this::doRefresh);

		try {
			boolean succeeded = initialFetch.get(initialFetchTimeout.toMillis(), TimeUnit.MILLISECONDS);
			if (!succeeded) {
				close();
				throw new SdkInitializationException(
						"Initial configuration fetch did not succeed within " + initialFetchTimeout + ".");
			}
		} catch (TimeoutException exception) {
			initialFetch.cancel(true);
			close();
			throw new SdkInitializationException(
					"Initial configuration fetch timed out after " + initialFetchTimeout + ".", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			initialFetch.cancel(true);
			close();
			throw new SdkInitializationException("Initialization was interrupted.", exception);
		} catch (ExecutionException exception) {
			close();
			throw new SdkInitializationException("Initial configuration fetch failed.", exception.getCause());
		}

		scheduleNext(refreshInterval);
	}

	/**
	 * Returns immediately; the first fetch and every subsequent one run on the
	 * background scheduler. Evaluations against this cache use caller-supplied
	 * fallbacks (NO_CONFIGURATION) until the first fetch completes.
	 */
	public void initializeAsync() {
		scheduler.execute(() -> {
			doRefresh();
			scheduleNext(refreshInterval);
		});
	}

	/** A caller-triggered refresh, e.g. LaunchFleetClient.refresh() - shares the exact same in-flight guard as the scheduler. */
	public boolean refresh() {
		return doRefresh();
	}

	public ConfigurationSnapshot currentSnapshot() {
		return snapshot.get();
	}

	/**
	 * Package-private test hook, not part of the SDK's public surface: lets
	 * ConfigurationCacheTest assert the backoff counter's lifecycle (increments on
	 * failure, resets on success) without waiting out real backoff delays to observe it
	 * indirectly through scheduling.
	 */
	int consecutiveFailures() {
		return consecutiveFailures.get();
	}

	@Override
	public void close() {
		closed = true;
		scheduler.shutdownNow();
		fetcher.close();
	}

	private void scheduleNext(Duration delay) {
		if (closed) {
			return;
		}

		scheduler.schedule(this::runScheduledRefresh, delay.toMillis(), TimeUnit.MILLISECONDS);
	}

	private void runScheduledRefresh() {
		if (closed) {
			return;
		}

		boolean succeeded = doRefresh();
		scheduleNext(succeeded ? refreshInterval : BackoffPolicy.nextDelay(consecutiveFailures.get()));
	}

	/**
	 * true means "no worse off than before" (either genuinely succeeded, or a refresh
	 * was already in flight so this call was a no-op) - false means this attempt itself
	 * observed a failure and the caller (scheduleNext) should back off.
	 */
	private boolean doRefresh() {
		if (!refreshInFlight.compareAndSet(false, true)) {
			return true;
		}

		try {
			FetchResult result = fetcher.fetch(lastKnownEtag.get());

			if (result instanceof FetchResult.NotModified) {
				consecutiveFailures.set(0);
				return true;
			}

			if (result instanceof FetchResult.Updated updated) {
				return applyUpdate(updated);
			}

			if (result instanceof FetchResult.Failed failed) {
				consecutiveFailures.incrementAndGet();
				errorListener.onError(failed.reason(), failed.cause());
				return false;
			}

			return false;
		} finally {
			refreshInFlight.set(false);
		}
	}

	private boolean applyUpdate(FetchResult.Updated updated) {
		ConfigurationSnapshot newSnapshot;
		try {
			newSnapshot = ConfigurationSnapshot.fromWire(updated.payload(), Instant.now());
		} catch (InvalidConfigurationException exception) {
			consecutiveFailures.incrementAndGet();
			errorListener.onError("Received malformed configuration; retaining previous snapshot.", exception);
			return false;
		}

		// Atomic replacement: a whole new, already-validated snapshot is installed in one
		// reference write - no evaluator thread ever observes a partially-updated snapshot.
		snapshot.set(newSnapshot);
		lastKnownEtag.set(updated.etag());
		consecutiveFailures.set(0);

		return true;
	}
}
