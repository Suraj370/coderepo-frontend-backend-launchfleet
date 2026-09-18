package com.launchfleet.backend.shared.openapi.schemas;

import java.util.List;

/** OpenAPI documentation only - mirrors FlagEvaluationSummaryResource's response shape. */
public record FlagEvaluationSummaryResponseSchema(long totalLast7Days, double percentChangeVsPriorPeriod,
		List<FlagEvaluationDailyCountSchema> byDay) {
}
