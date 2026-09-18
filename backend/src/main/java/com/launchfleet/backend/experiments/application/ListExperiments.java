package com.launchfleet.backend.experiments.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.experiments.ports.ExperimentStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;

@Component
public class ListExperiments {

	private final ExperimentLookup lookup;

	private final ExperimentStore experimentStore;

	ListExperiments(ExperimentLookup lookup, ExperimentStore experimentStore) {
		this.lookup = lookup;
		this.experimentStore = experimentStore;
	}

	public List<Experiment> execute(String projectKey) {
		ProjectRef project = lookup.resolveProject(projectKey);

		return experimentStore.findByProjectId(project.id());
	}
}
