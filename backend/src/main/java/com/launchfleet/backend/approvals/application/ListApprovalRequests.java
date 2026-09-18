package com.launchfleet.backend.approvals.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;

@Component
public class ListApprovalRequests {

	private final ApprovalRequestLookup lookup;

	private final ApprovalRequestStore approvalRequestStore;

	ListApprovalRequests(ApprovalRequestLookup lookup, ApprovalRequestStore approvalRequestStore) {
		this.lookup = lookup;
		this.approvalRequestStore = approvalRequestStore;
	}

	public List<ApprovalRequest> execute(String projectKey) {
		ProjectRef project = lookup.resolveProject(projectKey);

		return approvalRequestStore.findByProjectId(project.id());
	}
}
