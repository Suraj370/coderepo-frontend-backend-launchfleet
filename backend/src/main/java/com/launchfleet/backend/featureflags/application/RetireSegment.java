package com.launchfleet.backend.featureflags.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.activity.ActivityAction;
import com.launchfleet.backend.activity.ActivityRecorder;
import com.launchfleet.backend.featureflags.domain.ConditionType;
import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.featureflags.ports.SegmentStore;
import com.launchfleet.backend.shared.ApiException;

/**
 * Retirement is a one-way lifecycle transition, not a delete (see Segment.retire).
 * Rejected outright if any flag's targeting rules, in any environment of this
 * project, still reference this segment via a SEGMENT_MATCH condition - retiring it
 * would silently change evaluation behavior for those rules.
 */
@Component
public class RetireSegment {

	private static final int CONFLICT = 409;

	private final SegmentStore segmentStore;

	private final FeatureFlagConfigStore featureFlagConfigStore;

	private final SegmentLookup lookup;

	private final ActivityRecorder activityLogService;

	RetireSegment(SegmentStore segmentStore, FeatureFlagConfigStore featureFlagConfigStore, SegmentLookup lookup,
			ActivityRecorder activityLogService) {
		this.segmentStore = segmentStore;
		this.featureFlagConfigStore = featureFlagConfigStore;
		this.lookup = lookup;
		this.activityLogService = activityLogService;
	}

	public Segment execute(String projectKey, String segmentKey, String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);
		Segment segment = lookup.resolveSegment(project, segmentKey);

		boolean referenced = featureFlagConfigStore.findByProjectId(project.id()).stream()
				.flatMap(config -> config.getTargetingRules().stream())
				.flatMap(rule -> rule.getConditions().stream())
				.anyMatch(condition -> condition.type() == ConditionType.SEGMENT_MATCH
						&& condition.values().contains(segment.getId()));

		if (referenced) {
			throw new ApiException(CONFLICT, "SEGMENT_IN_USE",
					"This segment is referenced by an active targeting rule and cannot be retired.");
		}

		try {
			segment.retire(actingUserId);
		} catch (IllegalStateException exception) {
			throw new ApiException(CONFLICT, "ALREADY_RETIRED", exception.getMessage());
		}

		Segment saved = segmentStore.save(segment);

		activityLogService.record(project.id(), actingUserId, ActivityAction.SEGMENT_RETIRED, "segment",
				saved.getKey(), null);

		return saved;
	}
}
