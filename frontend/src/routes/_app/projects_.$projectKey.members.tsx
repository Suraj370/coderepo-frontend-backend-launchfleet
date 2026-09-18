import { createFileRoute } from "@tanstack/react-router"

import { ProjectMembersPage } from "@/pages/projects/ProjectMembersPage"

export const Route = createFileRoute("/_app/projects_/$projectKey/members")({
  component: ProjectMembersPage,
})
