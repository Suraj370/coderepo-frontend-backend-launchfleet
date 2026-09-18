package com.launchfleet.backend.experiments.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.featureflags.ports.ProjectRef;

@Component
public class GetExperiment {

	private final ExperimentLookup lookup;

	GetExperiment(ExperimentLookup lookup) {
		this.lookup = lookup;
	}

	public Experiment execute(String projectKey, String experimentKey) {
		ProjectRef project = lookup.resolveProject(projectKey);

		return lookup.resolveExperiment(project, experimentKey);
	}
}
