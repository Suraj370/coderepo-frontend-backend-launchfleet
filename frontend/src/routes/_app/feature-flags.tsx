import { createFileRoute } from "@tanstack/react-router"

import { FeatureFlagsPage } from "@/pages/feature-flags/FeatureFlagsPage"

export const Route = createFileRoute("/_app/feature-flags")({
  component: FeatureFlagsPage,
})
