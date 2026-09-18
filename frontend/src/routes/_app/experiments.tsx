import { createFileRoute } from "@tanstack/react-router"

import { ExperimentsPage } from "@/pages/experiments/ExperimentsPage"

export const Route = createFileRoute("/_app/experiments")({
  component: ExperimentsPage,
})
