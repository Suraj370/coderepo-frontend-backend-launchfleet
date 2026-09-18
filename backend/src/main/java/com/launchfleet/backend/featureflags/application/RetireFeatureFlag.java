package com.launchfleet.backend.featureflags.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.activity.ActivityAction;
import com.launchfleet.backend.activity.ActivityRecorder;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.ports.FeatureFlagRetiredListener;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

@Component
public class RetireFeatureFlag {

	private static final int CONFLICT = 409;

	private final FeatureFlagStore featureFlagStore;

	private final FeatureFlagLookup lookup;

	private final List<FeatureFlagRetiredListener> listeners;

	private final ActivityRecorder activityLogService;

	RetireFeatureFlag(FeatureFlagStore featureFlagStore, FeatureFlagLookup lookup,
			List<FeatureFlagRetiredListener> listeners, ActivityRecorder activityLogService) {
		this.featureFlagStore = featureFlagStore;
		this.lookup = lookup;
		this.listeners = listeners;
		this.activityLogService = activityLogService;
	}

	public FeatureFlagView execute(String projectKey, String flagKey, String actingUserId) {
		ProjectRef project = lookup.resolveProject(projectKey);
		FeatureFlag flag = lookup.resolveFlag(project, flagKey);

		try {
			flag.retire(actingUserId);
		} catch (IllegalStateException exception) {
			throw new ApiException(CONFLICT, "ALREADY_RETIRED", exception.getMessage());
		}

		FeatureFlag saved = featureFlagStore.save(flag);

		for (FeatureFlagRetiredListener listener : listeners) {
			listener.onFeatureFlagRetired(project.id(), saved.getId(), saved.getKey(), actingUserId);
		}

		activityLogService.record(project.id(), actingUserId, ActivityAction.FLAG_RETIRED, "flag", saved.getKey(),
				null);

		return lookup.viewOf(saved, project);
	}
}
