import { createFileRoute } from "@tanstack/react-router"

import { FeatureFlagDetailPage } from "@/pages/feature-flags/FeatureFlagDetailPage"

export const Route = createFileRoute("/_app/feature-flags_/$flagKey")({
  component: FeatureFlagDetailPage,
})
