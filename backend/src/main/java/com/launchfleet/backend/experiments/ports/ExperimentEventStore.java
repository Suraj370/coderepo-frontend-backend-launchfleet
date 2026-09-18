package com.launchfleet.backend.experiments.ports;

import com.launchfleet.backend.experiments.domain.ExperimentEvent;

/**
 * The persistence boundary for ExperimentEvent - raw events only (locked decision
 * 7/10), no rollup collection. Metric counts are derived at query time via these
 * methods, which the Mongo adapter implements with a plain count/distinct-count
 * query, not a background aggregation job.
 */
public interface ExperimentEventStore {

	ExperimentEvent save(ExperimentEvent event);

	/** The number of distinct users who fired eventName for this experiment/variant - i.e. conversionCount, not raw event volume. */
	long countDistinctUsersByExperimentIdAndVariantIdAndEventName(String experimentId, String variantId,
			String eventName);

	void deleteAll();

}
