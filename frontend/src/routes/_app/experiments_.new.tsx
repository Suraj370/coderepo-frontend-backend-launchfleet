import { createFileRoute } from "@tanstack/react-router"

import { CreateExperimentPage } from "@/pages/experiments/CreateExperimentPage"

export const Route = createFileRoute("/_app/experiments_/new")({
  component: CreateExperimentPage,
})
