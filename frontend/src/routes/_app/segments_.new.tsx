import { createFileRoute } from "@tanstack/react-router"

import { CreateSegmentPage } from "@/pages/segments/SegmentFormPage"

export const Route = createFileRoute("/_app/segments_/new")({
  component: CreateSegmentPage,
})
