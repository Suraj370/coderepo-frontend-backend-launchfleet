import { createFileRoute } from "@tanstack/react-router"

import { EditTargetingRulePage } from "@/pages/feature-flags/TargetingRuleFormPage"

export const Route = createFileRoute(
  "/_app/feature-flags_/$flagKey_/environments/$environmentKey/targeting-rules/$ruleId/edit",
)({
  component: EditTargetingRulePage,
})
