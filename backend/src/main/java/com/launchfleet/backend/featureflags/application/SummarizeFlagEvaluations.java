package com.launchfleet.backend.featureflags.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.ports.FlagEvaluationEventStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;

/**
 * A dashboard-facing aggregate over the last 7 days of evaluation events, plus the
 * percentage change against the preceding 7 days - computed at query time via
 * plain count queries (see FlagEvaluationEventStore), not a background rollup job.
 */
@Component
public class SummarizeFlagEvaluations {

	private static final int DAYS = 7;

	private static final long SECONDS_PER_DAY = 24L * 60 * 60;

	private final FeatureFlagLookup lookup;

	private final FlagEvaluationEventStore flagEvaluationEventStore;

	SummarizeFlagEvaluations(FeatureFlagLookup lookup, FlagEvaluationEventStore flagEvaluationEventStore) {
		this.lookup = lookup;
		this.flagEvaluationEventStore = flagEvaluationEventStore;
	}

	public record DailyCount(LocalDate date, long count) {
	}

	public record Summary(long totalLast7Days, double percentChangeVsPriorPeriod, List<DailyCount> byDay) {
	}

	public Summary execute(String projectKey) {
		ProjectRef project = lookup.resolveProject(projectKey);

		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		List<DailyCount> byDay = new ArrayList<>();
		long totalLast7Days = 0;

		for (int i = DAYS - 1; i >= 0; i--) {
			LocalDate day = today.minusDays(i);
			Instant from = day.atStartOfDay(ZoneOffset.UTC).toInstant();
			Instant to = from.plusSeconds(SECONDS_PER_DAY);
			long count = flagEvaluationEventStore.countByProjectIdAndTimestampBetween(project.id(), from, to);
			byDay.add(new DailyCount(day, count));
			totalLast7Days += count;
		}

		Instant priorPeriodStart = today.minusDays(2L * DAYS - 1).atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant priorPeriodEnd = today.minusDays(DAYS - 1).atStartOfDay(ZoneOffset.UTC).toInstant();
		long priorPeriodTotal = flagEvaluationEventStore.countByProjectIdAndTimestampBetween(project.id(),
				priorPeriodStart, priorPeriodEnd);

		double percentChange = priorPeriodTotal == 0 ? (totalLast7Days == 0 ? 0.0 : 100.0)
				: ((double) (totalLast7Days - priorPeriodTotal) / priorPeriodTotal) * 100.0;

		return new Summary(totalLast7Days, percentChange, byDay);
	}
}
