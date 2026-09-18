import { createFileRoute } from "@tanstack/react-router"

import { ExperimentDetailPage } from "@/pages/experiments/ExperimentDetailPage"

export const Route = createFileRoute("/_app/experiments_/$experimentKey")({
  component: ExperimentDetailPage,
})
