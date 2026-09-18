package com.launchfleet.backend.activity;

import org.springframework.stereotype.Service;

import com.launchfleet.backend.users.UserRepository;

/**
 * Called directly at the end of a command's execute() - matching this codebase's
 * established convention for cross-cutting concerns (see EnvironmentCreatedListener/
 * EnvironmentRetiredListener/FeatureFlagRetiredListener), not Spring's @EventListener.
 */
@Service
public class ActivityLogService implements ActivityRecorder {

	private static final String UNKNOWN_ACTOR = "Unknown user";

	private final ActivityLogRepository activityLogRepository;

	private final UserRepository userRepository;

	public ActivityLogService(ActivityLogRepository activityLogRepository, UserRepository userRepository) {
		this.activityLogRepository = activityLogRepository;
		this.userRepository = userRepository;
	}

	@Override
	public void record(String projectId, String actorUserId, ActivityAction action, String subjectType,
			String subjectKey, String environmentId) {
		String actorName = userRepository.findById(actorUserId).map(user -> user.getName()).orElse(UNKNOWN_ACTOR);

		activityLogRepository.save(
				ActivityLog.record(projectId, actorUserId, actorName, action, subjectType, subjectKey, environmentId));
	}
}
