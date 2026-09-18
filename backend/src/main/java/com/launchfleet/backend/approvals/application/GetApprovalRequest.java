package com.launchfleet.backend.approvals.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.featureflags.ports.ProjectRef;

@Component
public class GetApprovalRequest {

	private final ApprovalRequestLookup lookup;

	GetApprovalRequest(ApprovalRequestLookup lookup) {
		this.lookup = lookup;
	}

	public ApprovalRequest execute(String projectKey, String requestId) {
		ProjectRef project = lookup.resolveProject(projectKey);

		return lookup.resolveRequest(project, requestId);
	}
}
