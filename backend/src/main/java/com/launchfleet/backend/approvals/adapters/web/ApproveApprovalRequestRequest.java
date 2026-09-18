package com.launchfleet.backend.approvals.adapters.web;

/** approvalComment is optional (locked architecture rule 18) - may be null/omitted entirely. */
public record ApproveApprovalRequestRequest(String approvalComment) {
}
