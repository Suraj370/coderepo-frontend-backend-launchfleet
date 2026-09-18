package com.launchfleet.backend.activity;

/**
 * The port every domain module depends on to record a notable action - kept
 * minimal and dependency-free (see ActivityLogService for the real Mongo-backed
 * implementation) so pure in-memory unit tests, this codebase's convention for
 * application-layer commands, don't need a real repository to construct one.
 */
public interface ActivityRecorder {

	void record(String projectId, String actorUserId, ActivityAction action, String subjectType, String subjectKey,
			String environmentId);

}
