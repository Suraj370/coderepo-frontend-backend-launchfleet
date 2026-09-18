package com.launchfleet.backend.featureflags.adapters.web;

import java.util.List;

import com.launchfleet.backend.featureflags.domain.FlagType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * variants is validated against type in FeatureFlag.create (domain), not here:
 * BOOLEAN flags must not supply variants, MULTIVARIANT flags must supply at least
 * two - a cross-field rule Bean Validation can't express cleanly on its own.
 */
public record CreateFeatureFlagRequest(

		@NotBlank String key,

		@NotBlank String name,

		String description,

		@NotNull FlagType type,

		List<@Valid VariantRequest> variants) {
}
