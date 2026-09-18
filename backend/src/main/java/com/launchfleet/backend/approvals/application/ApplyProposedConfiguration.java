package com.launchfleet.backend.approvals.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.domain.ProposedConfig;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.FlagStatus;
import com.launchfleet.backend.featureflags.ports.EnvironmentLookup;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;

/**
 * The one place an approval request's proposed configuration is actually applied to
 * the active FeatureFlagConfig - shared by ApproveApprovalRequest (immediate) and
 * ApplyDueScheduledApprovals (scheduled), since sections 9-13 of the locked
 * architecture describe the identical sequence of checks for both: flag active,
 * environment active, version still matches baseConfigVersion, then apply.
 *
 * Never applies over a version that has moved (locked architecture rule 20's
 * invariant: never apply a proposal based on V10 over an active V11 configuration) -
 * the version check happens twice, once here as a fast-path read and again as the
 * actual write's own condition (FeatureFlagConfigStore.applyIfCurrentVersion), so a
 * write that races between this method's read and its own write is still caught
 * rather than silently overwriting newer configuration.
 */
@Component
class ApplyProposedConfiguration {

	private final FeatureFlagStore featureFlagStore;

	private final FeatureFlagConfigStore featureFlagConfigStore;

	private final EnvironmentLookup environmentLookup;

	ApplyProposedConfiguration(FeatureFlagStore featureFlagStore, FeatureFlagConfigStore featureFlagConfigStore,
			EnvironmentLookup environmentLookup) {
		this.featureFlagStore = featureFlagStore;
		this.featureFlagConfigStore = featureFlagConfigStore;
		this.environmentLookup = environmentLookup;
	}

	/**
	 * The real, writing path: re-checks applicability (see checkApplicability) and, if
	 * still applicable, performs the actual conditional write. Used at the moment of
	 * real application only - ApproveApprovalRequest (immediate) and
	 * ApplyDueScheduledApprovals (scheduled).
	 */
	ApplyOutcome apply(ApprovalRequest request) {
		Lookup lookup = resolve(request);
		if (lookup.earlyOutcome() != null) {
			return lookup.earlyOutcome();
		}

		FeatureFlagConfig current = lookup.config();
		ProposedConfig proposed = request.getProposedConfig();
		String actingUserId = request.getReviewedBy();

		try {
			current.applyProposedConfiguration(proposed.enabled(), proposed.defaultVariantId(),
					proposed.targetingRules(), proposed.rollout(), actingUserId);
		} catch (IllegalArgumentException exception) {
			return new ApplyOutcome.Failed(exception.getMessage());
		}

		return featureFlagConfigStore.applyIfCurrentVersion(current, request.getBaseConfigVersion())
				.<ApplyOutcome>map(applied -> new ApplyOutcome.Applied(applied.getVersion()))
				.orElseGet(ApplyOutcome.Stale::new);
	}

	/**
	 * A read-only pre-check, no write: used before scheduling (locked architecture
	 * rule 8/11 - "at ... scheduling ... time, compare [baseConfigVersion] with the
	 * current active configuration version"). Returns Applied(currentVersion) to mean
	 * "still applicable as of this read" - the caller (ScheduleApprovalRequest) never
	 * uses that version number, only whether the outcome is Applied at all.
	 */
	ApplyOutcome checkApplicability(ApprovalRequest request) {
		Lookup lookup = resolve(request);
		if (lookup.earlyOutcome() != null) {
			return lookup.earlyOutcome();
		}

		return new ApplyOutcome.Applied(lookup.config().getVersion());
	}

	private Lookup resolve(ApprovalRequest request) {
		FeatureFlag flag = featureFlagStore.findById(request.getFeatureFlagId()).orElse(null);
		if (flag == null || flag.getStatus() == FlagStatus.RETIRED) {
			return new Lookup(null, new ApplyOutcome.FlagRetired());
		}

		EnvironmentRef environment = environmentLookup.findById(request.getEnvironmentId()).orElse(null);
		if (environment == null || !environment.active()) {
			return new Lookup(null, new ApplyOutcome.EnvironmentRetired());
		}

		FeatureFlagConfig current = featureFlagConfigStore
				.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id()).orElse(null);
		if (current == null || current.getVersion() != request.getBaseConfigVersion()) {
			return new Lookup(null, new ApplyOutcome.Stale());
		}

		return new Lookup(current, null);
	}

	private record Lookup(FeatureFlagConfig config, ApplyOutcome earlyOutcome) {
	}
}
