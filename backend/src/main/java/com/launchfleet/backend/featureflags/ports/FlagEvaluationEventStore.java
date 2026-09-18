package com.launchfleet.backend.featureflags.ports;

import java.time.Instant;

import com.launchfleet.backend.featureflags.domain.FlagEvaluationEvent;

/**
 * The persistence boundary for FlagEvaluationEvent - raw events only, mirroring
 * ExperimentEventStore. Counts are derived at query time via plain count queries,
 * not a background rollup job.
 */
public interface FlagEvaluationEventStore {

	FlagEvaluationEvent save(FlagEvaluationEvent event);

	long countByProjectIdAndTimestampBetween(String projectId, Instant from, Instant to);

	void deleteAll();

}
