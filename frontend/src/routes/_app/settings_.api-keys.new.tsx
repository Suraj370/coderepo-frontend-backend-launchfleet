import { createFileRoute } from "@tanstack/react-router"

import { CreateApiKeyPage } from "@/pages/settings/CreateApiKeyPage"

export const Route = createFileRoute("/_app/settings_/api-keys/new")({
  component: CreateApiKeyPage,
})
