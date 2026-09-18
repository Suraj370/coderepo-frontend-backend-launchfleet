package com.launchfleet.backend.featureflags.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.ports.ProjectRef;

@Component
public class GetSegment {

	private final SegmentLookup lookup;

	GetSegment(SegmentLookup lookup) {
		this.lookup = lookup;
	}

	public Segment execute(String projectKey, String segmentKey) {
		ProjectRef project = lookup.resolveProject(projectKey);

		return lookup.resolveSegment(project, segmentKey);
	}
}
