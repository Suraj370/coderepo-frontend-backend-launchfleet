import { createFileRoute } from "@tanstack/react-router"

import { CreateProjectPage } from "@/pages/projects/CreateProjectPage"

export const Route = createFileRoute("/_app/projects_/new")({
  component: CreateProjectPage,
})
