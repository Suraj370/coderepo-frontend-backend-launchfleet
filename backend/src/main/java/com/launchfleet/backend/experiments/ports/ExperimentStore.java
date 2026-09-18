package com.launchfleet.backend.experiments.ports;

import java.util.List;
import java.util.Optional;

import com.launchfleet.backend.experiments.domain.Experiment;

/**
 * The persistence boundary for Experiment - mirrors FeatureFlagStore/
 * ApprovalRequestStore's shape exactly. save() enforces optimistic versioning for
 * existing experiments (locked decision 16) - see MongoExperimentStore's Javadoc.
 */
public interface ExperimentStore {

	Optional<Experiment> findById(String id);

	Optional<Experiment> findByProjectIdAndKey(String projectId, String key);

	List<Experiment> findByProjectId(String projectId);

	/** DRAFT or RUNNING experiments referencing this flag - used by the flag-retirement cascade. */
	List<Experiment> findActiveByFeatureFlagId(String featureFlagId);

	/** DRAFT or RUNNING experiments in this environment - used by the environment-retirement cascade. */
	List<Experiment> findActiveByEnvironmentId(String environmentId);

	Experiment save(Experiment experiment);

	void deleteAll();

}
