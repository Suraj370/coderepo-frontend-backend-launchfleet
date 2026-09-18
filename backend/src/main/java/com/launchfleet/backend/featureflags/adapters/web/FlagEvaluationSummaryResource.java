package com.launchfleet.backend.featureflags.adapters.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.launchfleet.backend.featureflags.application.SummarizeFlagEvaluations;
import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;
import com.launchfleet.backend.shared.openapi.schemas.FlagEvaluationSummaryResponseSchema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Dashboard-facing evaluation-volume summary (last 7 days, day-bucketed, plus %
 * change vs. the prior 7 days) - fed by FlagEvaluationResource's SDK-facing
 * counter. Expect this to read as empty/zero until a real SDK consumer actually
 * calls that endpoint.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/flags/evaluations")
@Tag(name = "Flag Evaluations", description = "Evaluation-volume analytics, fed by the SDK-facing evaluation counter.")
@SecurityRequirement(name = OpenApiConfig.DASHBOARD_SESSION_SCHEME)
public class FlagEvaluationSummaryResource {

	private final SummarizeFlagEvaluations summarizeFlagEvaluations;

	public FlagEvaluationSummaryResource(SummarizeFlagEvaluations summarizeFlagEvaluations) {
		this.summarizeFlagEvaluations = summarizeFlagEvaluations;
	}

	@GetMapping("/summary")
	@PreAuthorize("@projectAccess.hasRole(authentication, #projectKey, T(com.launchfleet.backend.users.Role).VIEWER)")
	@Operation(summary = "Get the 7-day evaluation-volume summary", description = "Requires VIEWER or above.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = FlagEvaluationSummaryResponseSchema.class))),
			@ApiResponse(responseCode = "401", description = "No dashboard session.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "Lacks VIEWER access to this project.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "404", description = "No project with that key.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public Map<String, Object> summary(@PathVariable @P("projectKey") String projectKey) {
		SummarizeFlagEvaluations.Summary summary = summarizeFlagEvaluations.execute(projectKey);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("totalLast7Days", summary.totalLast7Days());
		body.put("percentChangeVsPriorPeriod", summary.percentChangeVsPriorPeriod());
		body.put("byDay", summary.byDay().stream().map(day -> {
			Map<String, Object> dayMap = new LinkedHashMap<>();
			dayMap.put("date", day.date());
			dayMap.put("count", day.count());

			return dayMap;
		}).toList());

		return Map.of("data", body);
	}
}
