import { createFileRoute } from "@tanstack/react-router"

import { CreateEnvironmentPage } from "@/pages/environments/CreateEnvironmentPage"

export const Route = createFileRoute("/_app/environments_/new")({
  component: CreateEnvironmentPage,
})
