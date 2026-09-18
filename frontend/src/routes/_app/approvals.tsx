import { createFileRoute } from "@tanstack/react-router"

import { ApprovalsPage } from "@/pages/approvals/ApprovalsPage"

export const Route = createFileRoute("/_app/approvals")({
  component: ApprovalsPage,
})
