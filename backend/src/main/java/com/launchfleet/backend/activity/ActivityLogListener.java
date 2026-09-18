package com.launchfleet.backend.activity;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.environments.ports.EnvironmentCreatedListener;
import com.launchfleet.backend.environments.ports.EnvironmentRetiredListener;

/** Free activity-log coverage for environment create/retire via the existing listener ports. */
@Component
public class ActivityLogListener implements EnvironmentCreatedListener, EnvironmentRetiredListener {

	private final ActivityRecorder activityLogService;

	public ActivityLogListener(ActivityRecorder activityLogService) {
		this.activityLogService = activityLogService;
	}

	@Override
	public void onEnvironmentCreated(String projectId, String environmentId, String environmentKey,
			String actingUserId) {
		activityLogService.record(projectId, actingUserId, ActivityAction.ENVIRONMENT_CREATED, "environment",
				environmentKey, environmentId);
	}

	@Override
	public void onEnvironmentRetired(String projectId, String environmentId, String environmentKey,
			String actingUserId) {
		activityLogService.record(projectId, actingUserId, ActivityAction.ENVIRONMENT_RETIRED, "environment",
				environmentKey, environmentId);
	}
}
