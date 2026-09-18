package com.launchfleet.backend.shared.openapi.schemas;

import java.time.LocalDate;

/** OpenAPI documentation only - mirrors SummarizeFlagEvaluations.DailyCount. */
public record FlagEvaluationDailyCountSchema(LocalDate date, long count) {
}
