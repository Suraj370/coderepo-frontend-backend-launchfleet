package com.launchfleet.backend.approvals.adapters.web;

import jakarta.validation.constraints.NotBlank;

/** rejectionComment is mandatory (locked architecture rule 17/18) - validated non-blank after trimming. */
public record RejectApprovalRequestRequest(@NotBlank String rejectionComment) {
}
