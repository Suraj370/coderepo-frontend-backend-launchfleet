package com.launchfleet.backend.featureflags.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.activity.ActivityAction;
import com.launchfleet.backend.activity.ActivityRecorder;
import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.featureflags.ports.SegmentStore;
import com.launchfleet.backend.shared.ApiException;

@Component
public class CreateSegment {

	private static final int CONFLICT = 409;

	private static final int BAD_REQUEST = 400;

	private final SegmentStore segmentStore;

	private final SegmentLookup lookup;

	private final ActivityRecorder activityLogService;

	CreateSegment(SegmentStore segmentStore, SegmentLookup lookup, ActivityRecorder activityLogService) {
		this.segmentStore = segmentStore;
		this.lookup = lookup;
		this.activityLogService = activityLogService;
	}

	public Segment execute(String projectKey, String key, String name, List<Condition> conditions,
			String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);

		if (segmentStore.existsByProjectIdAndKey(project.id(), key)) {
			throw new ApiException(CONFLICT, "SEGMENT_KEY_TAKEN",
					"A segment with this key already exists in this project.");
		}

		try {
			Segment segment = Segment.create(project.id(), key, name, conditions, actingUserId);
			Segment saved = segmentStore.save(segment);

			activityLogService.record(project.id(), actingUserId, ActivityAction.SEGMENT_CREATED, "segment",
					saved.getKey(), null);

			return saved;
		} catch (IllegalArgumentException exception) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
		}
	}
}
