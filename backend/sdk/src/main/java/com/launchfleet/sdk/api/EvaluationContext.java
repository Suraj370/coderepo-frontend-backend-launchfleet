package com.launchfleet.sdk.api;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The SDK-side user context - deliberately as narrow as the server's own targeting
 * model (see the backend's Condition javadoc): a userKey plus an open string-keyed
 * attribute map, no typed schema, no new operators. Immutable once built: a context is
 * never mutated mid-evaluation, which matters for determinism under concurrent use.
 *
 * A null attribute value is treated as "attribute absent", not as a literal null -
 * there is nothing server-side for a null comparison to mean, so it's dropped at
 * construction rather than carried through to evaluation. Collection/Map-valued
 * attributes are rejected outright (IllegalArgumentException): the current targeting
 * language has no "attribute is a list" semantics to evaluate them against, and
 * silently stringifying a collection would be misleading rather than useful.
 */
public final class EvaluationContext {

	private final String userKey;

	private final Map<String, Object> attributes;

	private EvaluationContext(String userKey, Map<String, Object> attributes) {
		this.userKey = userKey;
		this.attributes = attributes;
	}

	public static EvaluationContext of(String userKey) {
		return new EvaluationContext(userKey, Map.of());
	}

	public static EvaluationContext of(String userKey, Map<String, Object> attributes) {
		Objects.requireNonNull(attributes, "attributes is required - pass Map.of() for none.");

		Map<String, Object> copy = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : attributes.entrySet()) {
			Object value = entry.getValue();
			if (value == null) {
				continue;
			}
			if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
				throw new IllegalArgumentException("Attribute '" + entry.getKey()
						+ "': collection/map attribute values are not supported in this phase.");
			}
			copy.put(entry.getKey(), value);
		}

		return new EvaluationContext(userKey, Map.copyOf(copy));
	}

	public String userKey() {
		return userKey;
	}

	public Map<String, Object> attributes() {
		return attributes;
	}

	public Object attribute(String name) {
		return attributes.get(name);
	}
}
