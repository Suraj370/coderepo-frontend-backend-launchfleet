package com.launchfleet.backend.projects.application;

import com.launchfleet.backend.projects.Project;
import com.launchfleet.backend.users.Role;

/** A project paired with the caller's own role on it. */
public record ProjectMembershipView(Project project, Role role) {
}
