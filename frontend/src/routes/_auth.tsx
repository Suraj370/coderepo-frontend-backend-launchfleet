import { createFileRoute, Link, Outlet } from "@tanstack/react-router"

import { BrandMark } from "@/components/layout/BrandMark"

export const Route = createFileRoute("/_auth")({
  component: AuthLayout,
})

function AuthLayout() {
  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-[#031c1b] p-6 font-sans text-[#f4f8f6]">
      <div className="absolute inset-0 h-[760px] bg-[radial-gradient(ellipse_at_75%_35%,#0b5440_0,transparent_44%),radial-gradient(ellipse_at_10%_80%,#07543c_0,transparent_38%),linear-gradient(135deg,#041c1b,#031c1b_70%)]" />
      <div className="relative z-10 w-full max-w-[400px]">
        <Link
          className="mb-8 flex items-center justify-center gap-2.5 text-[21px] font-bold no-underline"
          to="/"
        >
          <BrandMark /> LaunchFleet
        </Link>
        <Outlet />
      </div>
    </div>
  )
}
