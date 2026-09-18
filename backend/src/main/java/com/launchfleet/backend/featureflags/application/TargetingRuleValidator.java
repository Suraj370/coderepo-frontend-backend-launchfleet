package com.launchfleet.backend.featureflags.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.ConditionType;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.domain.SegmentStatus;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.featureflags.ports.SegmentStore;
import com.launchfleet.backend.shared.ApiException;

/**
 * Cross-aggregate validation shared by AddTargetingRule/UpdateTargetingRule - neither
 * TargetingRule nor FeatureFlagConfig has the references needed to check these
 * themselves (same reasoning as FeatureFlagConfig.updateDefaultVariant not checking
 * variant ownership on its own). Not a general-purpose service: it only validates,
 * it doesn't orchestrate a use case.
 */
@Component
public class TargetingRuleValidator {

	private static final int BAD_REQUEST = 400;

	private final SegmentStore segmentStore;

	public TargetingRuleValidator(SegmentStore segmentStore) {
		this.segmentStore = segmentStore;
	}

	public void validateVariantBelongsToFlag(FeatureFlag flag, String variantId) {
		boolean belongsToFlag = flag.getVariants().stream().anyMatch(variant -> variant.id().equals(variantId));

		if (!belongsToFlag) {
			throw new ApiException(BAD_REQUEST, "INVALID_VARIANT", "That variant does not belong to this flag.");
		}
	}

	public void validateSegmentReferences(ProjectRef project, List<Condition> conditions) {
		for (Condition condition : conditions) {
			if (condition.type() != ConditionType.SEGMENT_MATCH) {
				continue;
			}

			String segmentId = condition.values().get(0);
			Segment segment = segmentStore.findById(segmentId)
					.orElseThrow(() -> new ApiException(BAD_REQUEST, "INVALID_SEGMENT",
							"Referenced segment does not exist."));

			if (!segment.getProjectId().equals(project.id())) {
				throw new ApiException(BAD_REQUEST, "INVALID_SEGMENT",
						"Referenced segment does not belong to this project.");
			}

			if (segment.getStatus() == SegmentStatus.RETIRED) {
				throw new ApiException(BAD_REQUEST, "SEGMENT_RETIRED",
						"A retired segment cannot be referenced by a targeting rule.");
			}
		}
	}
}
