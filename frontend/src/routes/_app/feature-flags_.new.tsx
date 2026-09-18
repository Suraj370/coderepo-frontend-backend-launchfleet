import { createFileRoute } from "@tanstack/react-router"

import { CreateFeatureFlagPage } from "@/pages/feature-flags/CreateFeatureFlagPage"

export const Route = createFileRoute("/_app/feature-flags_/new")({
  component: CreateFeatureFlagPage,
})
