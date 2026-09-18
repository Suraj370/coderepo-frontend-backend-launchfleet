package com.launchfleet.backend.featureflags.adapters.web;

import java.util.List;

import com.launchfleet.backend.featureflags.domain.ConditionOperator;
import com.launchfleet.backend.featureflags.domain.ConditionType;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * attribute/values shape is validated against type/operator in Condition's compact
 * constructor (domain), not here - a cross-field rule Bean Validation can't express
 * cleanly on its own.
 */
public record ConditionRequest(

		@NotNull ConditionType type,

		String attribute,

		@NotNull ConditionOperator operator,

		@NotEmpty List<String> values) {
}
