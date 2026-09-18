package com.launchfleet.backend.featureflags.adapters.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * PUT replaces the complete allocation list - no partial-update shape exists, since
 * Rollout's "sums to exactly 10000" invariant can't be validated from a partial list
 * (see Rollout's Javadoc).
 */
public record RolloutRequest(

		@NotEmpty List<@Valid AllocationRequest> allocations) {
}
