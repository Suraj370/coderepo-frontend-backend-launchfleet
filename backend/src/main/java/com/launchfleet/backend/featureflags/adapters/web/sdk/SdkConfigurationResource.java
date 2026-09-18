package com.launchfleet.backend.featureflags.adapters.web.sdk;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.launchfleet.backend.featureflags.application.GetSdkConfiguration;
import com.launchfleet.backend.featureflags.application.SdkConfigurationResult;
import com.launchfleet.backend.featureflags.application.SdkFlagView;
import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.Rollout;
import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.domain.TargetingRule;
import com.launchfleet.backend.featureflags.domain.Variant;
import com.launchfleet.backend.sdk.SdkCredential;
import com.launchfleet.backend.shared.openapi.OpenApiConfig;
import com.launchfleet.backend.shared.openapi.schemas.ErrorResponseSchema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The SDK-facing configuration distribution endpoint. Lives entirely under the
 * existing sdkFilterChain (SecurityConstants.SDK_MATCHER = "/api/v1/sdk/**"), which
 * already requires SDK_SERVER for every request on this matcher by default - no new
 * SecurityFilterChain, no @PreAuthorize, no change to SdkCredentialAuthenticationFilter/
 * SdkAuthenticationToken. That chain is stateless and header-only: it never reads
 * cookies, so a dashboard session can never reach this route (see SecurityBoundaryTest).
 *
 * Project/environment scope comes ONLY from the authenticated SdkCredential's own
 * projectKey/environmentKey (its principal on the SecurityContext) - never from a path,
 * query, or body parameter a caller could tamper with to request a different
 * environment. This is read-only: the only use case wired here is GetSdkConfiguration,
 * which has no path to any mutation/management use case.
 */
@RestController
@RequestMapping("/api/v1/sdk")
@Tag(name = "SDK", description = "Endpoints called by SDK_SERVER-authenticated customer application code (not the dashboard, not necessarily via the launchfleet-sdk Java artifact): configuration distribution and experiment assignment/event recording. Project/environment scope always comes from the authenticated credential, never from a request parameter.")
@SecurityRequirement(name = OpenApiConfig.SDK_SERVER_SCHEME)
public class SdkConfigurationResource {

	private final GetSdkConfiguration getSdkConfiguration;

	public SdkConfigurationResource(GetSdkConfiguration getSdkConfiguration) {
		this.getSdkConfiguration = getSdkConfiguration;
	}

	@GetMapping("/config")
	@Operation(summary = "Get the full flag/segment configuration for local SDK evaluation", description = "Unlike every other endpoint in this API, this response is NOT wrapped in {\"data\": ...} - the body is the configuration document directly. Intended for local (client-side-of-the-SDK) evaluation: the SDK caches this snapshot and evaluates flags itself rather than calling this API per evaluation. Supports conditional requests via ETag/If-None-Match, where the ETag is the configuration version.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The current configuration.", content = @Content(schema = @Schema(implementation = SdkConfigurationResponse.class))),
			@ApiResponse(responseCode = "304", description = "If-None-Match matched the current version - body omitted, same ETag returned."),
			@ApiResponse(responseCode = "401", description = "Missing/invalid/expired/inactive SDK_SERVER credential.", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
			@ApiResponse(responseCode = "403", description = "A valid credential that is not SDK_SERVER (e.g. SDK_CLIENT_SIDE).", content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))) })
	public ResponseEntity<SdkConfigurationResponse> getConfiguration(@AuthenticationPrincipal SdkCredential credential,
			@Parameter(description = "A previously-received ETag - if it matches the current version, a 304 with no body is returned instead.") @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
		SdkConfigurationResult result = getSdkConfiguration.execute(credential.getProjectKey(),
				credential.getEnvironmentKey());
		String etag = "\"" + result.version() + "\"";

		if (etag.equals(ifNoneMatch)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
		}

		return ResponseEntity.ok().eTag(etag).body(toResponse(result));
	}

	private SdkConfigurationResponse toResponse(SdkConfigurationResult result) {
		return new SdkConfigurationResponse(result.environmentId(), result.environmentKey(), result.version(),
				result.generatedAt(), result.flags().stream().map(this::toResponse).toList(),
				result.segments().stream().map(this::toResponse).toList());
	}

	private SdkFlagResponse toResponse(SdkFlagView flag) {
		return new SdkFlagResponse(flag.key(), flag.enabled(), toResponse(flag.defaultVariant()),
				flag.variants().stream().map(this::toResponse).toList(),
				flag.targetingRules().stream().map(this::toResponse).toList(),
				flag.rollout() == null ? null : toResponse(flag.rollout()), flag.version());
	}

	private SdkVariantResponse toResponse(Variant variant) {
		return new SdkVariantResponse(variant.id(), variant.key(), variant.name(), variant.value());
	}

	private SdkTargetingRuleResponse toResponse(TargetingRule rule) {
		List<SdkConditionResponse> conditions = rule.getConditions().stream().map(this::toResponse).toList();

		return new SdkTargetingRuleResponse(rule.getId(), rule.getPriority(), conditions, rule.getVariantId());
	}

	private SdkConditionResponse toResponse(Condition condition) {
		return new SdkConditionResponse(condition.type().name(), condition.attribute(), condition.operator().name(),
				condition.values());
	}

	private SdkRolloutResponse toResponse(Rollout rollout) {
		return new SdkRolloutResponse(rollout.sortedByVariantId().stream().map(this::toResponse).toList());
	}

	private SdkAllocationResponse toResponse(Allocation allocation) {
		return new SdkAllocationResponse(allocation.variantId(), allocation.percentage());
	}

	private SdkSegmentResponse toResponse(Segment segment) {
		List<SdkConditionResponse> conditions = segment.getConditions().stream().map(this::toResponse).toList();

		return new SdkSegmentResponse(segment.getId(), segment.getKey(), conditions);
	}
}
