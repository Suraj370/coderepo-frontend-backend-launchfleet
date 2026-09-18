package com.launchfleet.backend.approvals.adapters.web;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/** approvalComment is optional; scheduledAt's "must be strictly in the future" rule is enforced in the domain (ApprovalRequest.scheduleFor). */
public record ScheduleApprovalRequestRequest(@NotNull Instant scheduledAt, String approvalComment) {
}
