import { createFileRoute } from "@tanstack/react-router"

import { SegmentsPage } from "@/pages/segments/SegmentsPage"

export const Route = createFileRoute("/_app/segments")({
  component: SegmentsPage,
})
