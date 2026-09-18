package com.launchfleet.backend.experiments.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.experiments.ports.ExperimentAssignmentStore;
import com.launchfleet.backend.experiments.ports.ExperimentEventStore;
import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.ports.ProjectRef;

/**
 * Computes the locked-decision-11 MVP metrics entirely from persisted assignments
 * and raw events at query time (locked decision 10) - no rollup collection, no
 * background aggregation, no statistical inference of any kind.
 */
@Component
public class GetExperimentMetrics {

	private final ExperimentLookup lookup;

	private final ExperimentAssignmentStore assignmentStore;

	private final ExperimentEventStore eventStore;

	GetExperimentMetrics(ExperimentLookup lookup, ExperimentAssignmentStore assignmentStore,
			ExperimentEventStore eventStore) {
		this.lookup = lookup;
		this.assignmentStore = assignmentStore;
		this.eventStore = eventStore;
	}

	public List<ExperimentVariantMetrics> execute(String projectKey, String experimentKey) {
		ProjectRef project = lookup.resolveProject(projectKey);
		Experiment experiment = lookup.resolveExperiment(project, experimentKey);

		if (experiment.getAllocation() == null) {
			return List.of();
		}

		return experiment.getAllocation().allocations().stream().map(Allocation::variantId)
				.map(variantId -> metricsFor(experiment, variantId)).toList();
	}

	private ExperimentVariantMetrics metricsFor(Experiment experiment, String variantId) {
		long assignedCount = assignmentStore.countByExperimentIdAndVariantId(experiment.getId(), variantId);
		long conversionCount = experiment.getConversionEventName() == null ? 0
				: eventStore.countDistinctUsersByExperimentIdAndVariantIdAndEventName(experiment.getId(), variantId,
						experiment.getConversionEventName());
		double conversionRate = assignedCount == 0 ? 0.0 : (double) conversionCount / (double) assignedCount;

		return new ExperimentVariantMetrics(variantId, assignedCount, conversionCount, conversionRate);
	}
}
