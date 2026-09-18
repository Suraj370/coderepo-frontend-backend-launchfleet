package com.launchfleet.backend.experiments.ports;

import java.util.Optional;

import com.launchfleet.backend.experiments.domain.ExperimentAssignment;

/**
 * The persistence boundary for ExperimentAssignment - a separate, high-cardinality
 * collection (locked decision 4), never embedded in Experiment.
 */
public interface ExperimentAssignmentStore {

	Optional<ExperimentAssignment> findByExperimentIdAndUserKey(String experimentId, String userKey);

	/**
	 * First-write-wins (locked decision 4/requirement 4): attempts to persist
	 * `candidate`, but if a concurrent call already established an assignment for the
	 * same (experimentId, userKey) - enforced by a MongoDB unique index, not
	 * application logic - returns the EXISTING, authoritative assignment instead of
	 * erroring or overwriting it. Callers never need to distinguish "I created it"
	 * from "someone else already had" - both return the one true assignment.
	 */
	ExperimentAssignment getOrCreate(ExperimentAssignment candidate);

	long countByExperimentId(String experimentId);

	long countByExperimentIdAndVariantId(String experimentId, String variantId);

	void deleteAll();

}
