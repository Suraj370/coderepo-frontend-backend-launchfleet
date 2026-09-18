package com.launchfleet.backend.approvals.application;

import com.launchfleet.backend.activity.ActivityAction;
import com.launchfleet.backend.activity.ActivityRecorder;

/** A no-op ActivityRecorder for testing application use cases without Spring or MongoDB. */
class InMemoryActivityRecorder implements ActivityRecorder {

	@Override
	public void record(String projectId, String actorUserId, ActivityAction action, String subjectType,
			String subjectKey, String environmentId) {
		// no-op - these tests exercise domain logic, not activity logging.
	}
}
