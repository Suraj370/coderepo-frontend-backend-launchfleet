import { createFileRoute } from "@tanstack/react-router"

import { ApprovalDetailPage } from "@/pages/approvals/ApprovalDetailPage"

export const Route = createFileRoute("/_app/approvals_/$requestId")({
  component: ApprovalDetailPage,
})
