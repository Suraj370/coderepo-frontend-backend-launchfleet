package com.launchfleet.sdk.api;

/**
 * Every reason earns its keep as something a caller can act on (see EvaluationDetail),
 * not merely a log label:
 *  - DISABLED: the flag is off - stop looking at targeting/rollout for an explanation.
 *  - TARGET_MATCH: a targeting rule fired.
 *  - ROLLOUT: no rule matched; the user was bucketed into a percentage rollout.
 *  - DEFAULT: nothing else applied (or a referenced variant/rule/rollout target was
 *    missing from the snapshot - treated as "didn't apply", not a crash).
 *  - FLAG_NOT_FOUND: the flag key isn't in the current configuration at all - distinct
 *    from DEFAULT, which is a real, meaningful evaluation outcome for a flag that does
 *    exist.
 *  - NO_USER_KEY: a rollout was configured for this flag but no usable userKey was
 *    given, so rollout bucketing could not run at all - distinct from ROLLOUT/DEFAULT so
 *    a caller can tell "no identity was given" from "targeting genuinely didn't match".
 *  - NO_CONFIGURATION: the SDK has not yet obtained (or lost) any usable configuration -
 *    kept separate from FLAG_NOT_FOUND rather than pretending the flag itself is absent.
 */
public enum EvaluationReason {

	DISABLED,

	TARGET_MATCH,

	ROLLOUT,

	DEFAULT,

	FLAG_NOT_FOUND,

	NO_USER_KEY,

	NO_CONFIGURATION

}
