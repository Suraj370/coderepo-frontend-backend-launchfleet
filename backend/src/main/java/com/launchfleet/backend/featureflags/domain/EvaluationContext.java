package com.launchfleet.backend.featureflags.domain;

import java.util.Map;

/**
 * The input to targeting evaluation: who/what is asking for a flag's value. Deliberately
 * NOT a reference to users.User or any dashboard-user type - a dashboard user is a
 * persisted identity with credentials and project memberships, while an evaluation
 * context is an ephemeral, caller-supplied set of attributes an SDK consumer passes at
 * evaluation time (which may not correspond to any dashboard user at all).
 */
public record EvaluationContext(String userKey, Map<String, Object> attributes) {

	public EvaluationContext {
		attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
	}
}
