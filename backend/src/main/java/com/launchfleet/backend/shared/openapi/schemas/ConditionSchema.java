package com.launchfleet.backend.shared.openapi.schemas;

import java.util.List;

import com.launchfleet.backend.featureflags.domain.ConditionOperator;
import com.launchfleet.backend.featureflags.domain.ConditionType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OpenAPI documentation only - mirrors the {@code Map<String,Object>} shape every
 * controller's own {@code toMap(Condition)} helper actually produces. Not used as
 * an actual controller return type; kept in sync by hand against those helpers.
 */
@Schema(description = "A single IF-clause within a targeting rule or a segment.")
public record ConditionSchema(

		@Schema(description = "How `values` is interpreted.") ConditionType type,

		@Schema(description = "Required for ATTRIBUTE conditions; absent otherwise.") String attribute,

		ConditionOperator operator,

		@Schema(description = "EQUALS requires exactly one value; IN requires at least one.") List<String> values) {
}
