package com.launchfleet.backend.featureflags.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.featureflags.ports.SegmentStore;

@Component
public class ListSegments {

	private final SegmentStore segmentStore;

	private final SegmentLookup lookup;

	ListSegments(SegmentStore segmentStore, SegmentLookup lookup) {
		this.segmentStore = segmentStore;
		this.lookup = lookup;
	}

	public List<Segment> execute(String projectKey) {
		ProjectRef project = lookup.resolveProject(projectKey);

		return segmentStore.findByProjectId(project.id());
	}
}
