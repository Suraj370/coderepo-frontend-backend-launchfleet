package com.launchfleet.sdk.configuration;

/** Mirrors the backend's ConditionType exactly - see FlagEvaluator for how each is resolved. */
public enum ConditionType {

	ATTRIBUTE,

	USER_KEY,

	SEGMENT_MATCH

}
