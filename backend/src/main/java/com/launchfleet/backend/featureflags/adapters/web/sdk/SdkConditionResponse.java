package com.launchfleet.backend.featureflags.adapters.web.sdk;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Mirrors Condition exactly, including SEGMENT_MATCH's values holding a segment's
 * internal id (looked up against SdkConfigurationResponse.segments()) rather than its
 * project-facing key - the SDK evaluator resolves it the same way the server does.
 *
 * type/operator are plain String here (already .name()'d before construction), not
 * the domain ConditionType/ConditionOperator enums - documented via
 * @Schema(allowableValues=...) below rather than changed to an enum type, since this
 * DTO's shape is existing SDK-facing wire behavior that isn't being changed.
 */
public record SdkConditionResponse(

		@Schema(allowableValues = { "ATTRIBUTE", "USER_KEY", "SEGMENT_MATCH" }) String type,

		String attribute,

		@Schema(allowableValues = { "EQUALS", "IN" }) String operator,

		List<String> values) {
}
