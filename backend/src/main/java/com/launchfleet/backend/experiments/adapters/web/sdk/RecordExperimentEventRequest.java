package com.launchfleet.backend.experiments.adapters.web.sdk;

import jakarta.validation.constraints.NotBlank;

/**
 * Deliberately has no variantId field at all (locked decision 9: "do not accept a
 * client-supplied variant as authoritative if it conflicts with the persisted
 * assignment") - the simplest possible way to guarantee that is to never read one
 * from the request in the first place. The variant is always resolved server-side
 * from the persisted ExperimentAssignment.
 */
public record RecordExperimentEventRequest(@NotBlank String userKey, @NotBlank String eventName) {
}
