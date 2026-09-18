import { createFileRoute, Outlet } from "@tanstack/react-router"

import { EnvironmentProvider } from "@/app/environment-context"
import { AppSidebar } from "@/components/layout/AppSidebar"
import { AppTopbar } from "@/components/layout/AppTopbar"
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar"

export const Route = createFileRoute("/_app")({
  component: AppLayout,
})

function AppLayout() {
  return (
    <EnvironmentProvider>
      <SidebarProvider>
        <AppSidebar />
        <SidebarInset>
          <AppTopbar />
          <div className="flex-1 p-6">
            <Outlet />
          </div>
        </SidebarInset>
      </SidebarProvider>
    </EnvironmentProvider>
  )
}
