import { createFileRoute } from "@tanstack/react-router"

import { EnvironmentsPage } from "@/pages/environments/EnvironmentsPage"

export const Route = createFileRoute("/_app/environments")({
  component: EnvironmentsPage,
})
