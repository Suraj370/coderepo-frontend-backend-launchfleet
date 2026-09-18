import { createFileRoute } from "@tanstack/react-router"

import { EditSegmentPage } from "@/pages/segments/SegmentFormPage"

export const Route = createFileRoute("/_app/segments_/$segmentKey/edit")({
  component: EditSegmentPage,
})
