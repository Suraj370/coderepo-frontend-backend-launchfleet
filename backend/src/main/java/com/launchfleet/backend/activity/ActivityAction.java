package com.launchfleet.backend.activity;

/** The fixed vocabulary of actions the activity feed records - a representative set of each domain's main lifecycle events, not every command in the system. */
public enum ActivityAction {

	FLAG_CREATED,

	FLAG_RETIRED,

	ENVIRONMENT_CREATED,

	ENVIRONMENT_RETIRED,

	SEGMENT_CREATED,

	SEGMENT_RETIRED,

	EXPERIMENT_CREATED,

	EXPERIMENT_STARTED,

	EXPERIMENT_COMPLETED,

	APPROVAL_SUBMITTED,

	APPROVAL_APPROVED,

	APPROVAL_REJECTED

}
