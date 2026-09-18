import { createFileRoute } from "@tanstack/react-router"

import { CreateTargetingRulePage } from "@/pages/feature-flags/TargetingRuleFormPage"

export const Route = createFileRoute(
  "/_app/feature-flags_/$flagKey_/environments/$environmentKey/targeting-rules/new",
)({
  component: CreateTargetingRulePage,
})
