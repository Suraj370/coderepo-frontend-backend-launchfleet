package com.launchfleet.backend.experiments.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.experiments.domain.ExperimentAssignment;
import com.launchfleet.backend.experiments.domain.ExperimentEvent;
import com.launchfleet.backend.experiments.domain.ExperimentStatus;
import com.launchfleet.backend.experiments.ports.ExperimentAssignmentStore;
import com.launchfleet.backend.experiments.ports.ExperimentEventStore;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Records a raw conversion event (locked decision 7). The variant is ALWAYS the
 * persisted assignment's variant (locked decision 9) - any client-supplied variant
 * hint in the request is never read here at all, which is the simplest possible
 * way to guarantee "do not accept a client-supplied variant as authoritative if it
 * conflicts with the persisted assignment": there is nothing to conflict with,
 * because nothing client-supplied is ever consulted.
 *
 * If no assignment exists for this (experiment, userKey), this fails explicitly
 * with ASSIGNMENT_NOT_FOUND rather than fabricating one - re-evaluating the flag or
 * silently creating a fresh assignment at conversion time would attribute the
 * conversion to a variant the user might never actually have been exposed to
 * (exactly what locked decision 9 forbids).
 */
@Component
public class RecordExperimentEvent {

	private static final int BAD_REQUEST = 400;

	private static final int CONFLICT = 409;

	private static final int NOT_FOUND = 404;

	private final ExperimentLookup lookup;

	private final ExperimentAssignmentStore assignmentStore;

	private final ExperimentEventStore eventStore;

	RecordExperimentEvent(ExperimentLookup lookup, ExperimentAssignmentStore assignmentStore,
			ExperimentEventStore eventStore) {
		this.lookup = lookup;
		this.assignmentStore = assignmentStore;
		this.eventStore = eventStore;
	}

	public ExperimentEvent execute(String projectKey, String environmentKey, String experimentKey, String userKey,
			String eventName) {
		if (userKey == null || userKey.isBlank()) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", "userKey is required.");
		}
		if (eventName == null || eventName.isBlank()) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", "eventName is required.");
		}

		ProjectRef project = lookup.resolveProject(projectKey);
		EnvironmentRef environment = lookup.resolveEnvironment(project, environmentKey);
		Experiment experiment = lookup.resolveExperiment(project, experimentKey);
		if (!experiment.getEnvironmentId().equals(environment.id())) {
			throw new ApiException(NOT_FOUND, "EXPERIMENT_NOT_FOUND", "No experiment with that key in this environment.");
		}
		if (experiment.getStatus() != ExperimentStatus.RUNNING) {
			throw new ApiException(CONFLICT, "EXPERIMENT_NOT_RUNNING",
					"This experiment is not currently running; events cannot be recorded for it.");
		}

		ExperimentAssignment assignment = assignmentStore.findByExperimentIdAndUserKey(experiment.getId(), userKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "ASSIGNMENT_NOT_FOUND",
						"No assignment exists for this user in this experiment; establish an assignment before recording events."));

		ExperimentEvent event = ExperimentEvent.create(project.id(), environment.id(), experiment.getId(), userKey,
				eventName, assignment.getVariantId());

		return eventStore.save(event);
	}
}
