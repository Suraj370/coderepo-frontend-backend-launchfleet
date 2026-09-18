package com.launchfleet.backend.approvals.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.ports.EnvironmentLookup;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;
import com.launchfleet.backend.featureflags.ports.ProjectLookup;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Shared, mechanical resolution ("load this or 404") used by every approvals use
 * case - mirrors featureflags.application.FeatureFlagLookup's role exactly, reusing
 * the SAME featureflags ports (ProjectLookup, EnvironmentLookup, FeatureFlagStore)
 * rather than a second set of project/environment/flag lookup abstractions.
 */
@Component
class ApprovalRequestLookup {

	private static final int NOT_FOUND = 404;

	private final ProjectLookup projectLookup;

	private final EnvironmentLookup environmentLookup;

	private final FeatureFlagStore featureFlagStore;

	private final ApprovalRequestStore approvalRequestStore;

	ApprovalRequestLookup(ProjectLookup projectLookup, EnvironmentLookup environmentLookup,
			FeatureFlagStore featureFlagStore, ApprovalRequestStore approvalRequestStore) {
		this.projectLookup = projectLookup;
		this.environmentLookup = environmentLookup;
		this.featureFlagStore = featureFlagStore;
		this.approvalRequestStore = approvalRequestStore;
	}

	ProjectRef resolveProject(String projectKey) {
		return projectLookup.findByKey(projectKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "PROJECT_NOT_FOUND", "No project with that key."));
	}

	FeatureFlag resolveFlag(ProjectRef project, String flagKey) {
		return featureFlagStore.findByProjectIdAndKey(project.id(), flagKey).orElseThrow(
				() -> new ApiException(NOT_FOUND, "FLAG_NOT_FOUND", "No flag with that key in this project."));
	}

	EnvironmentRef resolveEnvironment(ProjectRef project, String environmentKey) {
		return environmentLookup.findByProjectIdAndKey(project.id(), environmentKey)
				.orElseThrow(() -> new ApiException(NOT_FOUND, "ENVIRONMENT_NOT_FOUND",
						"No environment '" + environmentKey + "' in this project."));
	}

	/** requestId is trusted to belong to `project` only after this check - see every use case that calls it. */
	ApprovalRequest resolveRequest(ProjectRef project, String requestId) {
		ApprovalRequest request = approvalRequestStore.findById(requestId).orElseThrow(
				() -> new ApiException(NOT_FOUND, "APPROVAL_REQUEST_NOT_FOUND", "No approval request with that id."));

		if (!request.getProjectId().equals(project.id())) {
			throw new ApiException(NOT_FOUND, "APPROVAL_REQUEST_NOT_FOUND", "No approval request with that id.");
		}

		return request;
	}
}
