package com.launchfleet.backend.featureflags.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.featureflags.ports.SegmentStore;
import com.launchfleet.backend.shared.ApiException;

@Component
public class UpdateSegment {

	private static final int BAD_REQUEST = 400;

	private final SegmentStore segmentStore;

	private final SegmentLookup lookup;

	UpdateSegment(SegmentStore segmentStore, SegmentLookup lookup) {
		this.segmentStore = segmentStore;
		this.lookup = lookup;
	}

	public Segment execute(String projectKey, String segmentKey, String name, List<Condition> conditions,
			String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);
		Segment segment = lookup.resolveSegment(project, segmentKey);

		try {
			segment.update(name, conditions, actingUserId);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}

		return segmentStore.save(segment);
	}
}
