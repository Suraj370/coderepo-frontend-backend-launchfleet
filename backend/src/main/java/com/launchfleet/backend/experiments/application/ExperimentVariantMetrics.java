package com.launchfleet.backend.experiments.application;

/**
 * The complete locked-decision-11 MVP metric set, per variant - nothing more
 * (no significance, no confidence intervals, no lift). conversionRate is 0.0 when
 * assignedCount is 0, never NaN/Infinity - see GetExperimentMetrics.
 */
public record ExperimentVariantMetrics(String variantId, long assignedCount, long conversionCount,
		double conversionRate) {
}
