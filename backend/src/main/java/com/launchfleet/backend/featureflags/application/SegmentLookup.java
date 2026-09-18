package com.launchfleet.backend.featureflags.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.ports.ProjectLookup;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.featureflags.ports.SegmentStore;
import com.launchfleet.backend.shared.ApiException;

/**
 * Shared, mechanical resolution used by the Segment use cases - mirrors
 * FeatureFlagLookup's role but is deliberately its own component rather than
 * sharing FeatureFlagLookup: Segment resolution has nothing to do with flags/
 * environments, and depending on FeatureFlagLookup here would be a backwards
 * dependency (targeting rules depend on segments, not the other way around).
 */
@Component
class SegmentLookup {

	private static final int NOT_FOUND = 404;

	private final SegmentStore segmentStore;

	private final ProjectLookup projectLookup;

	SegmentLookup(SegmentStore segmentStore, ProjectLookup projectLookup) {
		this.segmentStore = segmentStore;
		this.projectLookup = projectLookup;
	}

	ProjectRef resolveProject(String projectKey) {
		return projectLookup.findByKey(projectKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "PROJECT_NOT_FOUND", "No project with that key."));
	}

	Segment resolveSegment(ProjectRef project, String segmentKey) {
		return segmentStore.findByProjectIdAndKey(project.id(), segmentKey).orElseThrow(
				() -> new ApiException(NOT_FOUND, "SEGMENT_NOT_FOUND", "No segment with that key in this project."));
	}
}
