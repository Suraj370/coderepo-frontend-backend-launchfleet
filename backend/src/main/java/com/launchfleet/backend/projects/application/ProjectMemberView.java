package com.launchfleet.backend.projects.application;

import com.launchfleet.backend.projects.ProjectMembership;

/** A membership paired with the resolved user's display name/email. */
public record ProjectMemberView(ProjectMembership membership, String userName, String userEmail) {
}
