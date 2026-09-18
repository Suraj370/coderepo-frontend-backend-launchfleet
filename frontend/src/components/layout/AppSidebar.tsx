import type { CSSProperties } from "react"
import {
  ChevronsUpDown,
  FlaskConical,
  FolderKanban,
  LayoutDashboard,
  LogOut,
  Network,
  Settings as SettingsIcon,
  ShieldCheck,
  ToggleRight,
  UsersRound,
  type LucideIcon,
} from "lucide-react"
import { Link, useNavigate } from "@tanstack/react-router"

import { BrandMark } from "@/components/layout/BrandMark"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuItem,
  sidebarMenuButtonVariants,
} from "@/components/ui/sidebar"
import { useLogout } from "@/features/auth/hooks/useLogout"
import { useSession } from "@/features/auth/hooks/useSession"

const navItems: { to: string; label: string; icon: LucideIcon }[] = [
  { to: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { to: "/feature-flags", label: "Feature Flags", icon: ToggleRight },
  { to: "/environments", label: "Environments", icon: Network },
  { to: "/segments", label: "Segments", icon: UsersRound },
  { to: "/experiments", label: "Experiments", icon: FlaskConical },
  { to: "/approvals", label: "Approvals", icon: ShieldCheck },
  { to: "/projects", label: "Projects", icon: FolderKanban },
  { to: "/settings", label: "Settings", icon: SettingsIcon },
]

const sidebarThemeVars = {
  "--sidebar": "#071d1c",
  "--sidebar-foreground": "#8fa8a1",
  "--sidebar-accent": "#0f2e29",
  "--sidebar-accent-foreground": "#4be08a",
  "--sidebar-border": "#173832",
  "--sidebar-ring": "#36e79a",
} as CSSProperties

function initialsOf(name: string) {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join("")
}

export function AppSidebar() {
  const navigate = useNavigate()
  const session = useSession()
  const logout = useLogout()
  const user = session.data?.user

  function handleSignOut() {
    logout.mutate(undefined, {
      onSuccess: () => {
        navigate({ to: "/login" })
      },
    })
  }

  return (
    <Sidebar collapsible="icon" style={sidebarThemeVars}>
      <SidebarHeader>
        <Link
          className="flex items-center gap-2 px-2 py-1.5 text-base font-bold text-white no-underline"
          to="/dashboard"
        >
          <BrandMark />
          <span className="group-data-[collapsible=icon]:hidden">LaunchFleet</span>
        </Link>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu className="gap-1">
              {navItems.map(({ to, label, icon: Icon }) => (
                <SidebarMenuItem key={to}>
                  <Link
                    className={sidebarMenuButtonVariants()}
                    activeProps={{ "data-active": "true" }}
                    to={to}
                  >
                    <Icon />
                    <span>{label}</span>
                  </Link>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarFooter>
        <DropdownMenuTrigger>
          <Button
            className="h-auto w-full justify-start gap-2 px-2 py-1.5 text-sidebar-foreground hover:bg-sidebar-accent hover:text-white data-open:bg-sidebar-accent data-open:text-white"
            variant="ghost"
          >
            <Avatar size="sm">
              <AvatarFallback className="bg-sidebar-accent text-sidebar-accent-foreground">
                {user ? initialsOf(user.name) : "…"}
              </AvatarFallback>
            </Avatar>
            <div className="flex min-w-0 flex-col items-start group-data-[collapsible=icon]:hidden">
              <span className="truncate text-sm font-medium text-white">{user?.name ?? "…"}</span>
              <span className="truncate text-xs text-sidebar-foreground/70">{user?.email}</span>
            </div>
            <ChevronsUpDown
              className="ml-auto shrink-0 text-sidebar-foreground/60 group-data-[collapsible=icon]:hidden"
              size={14}
            />
          </Button>
          <DropdownMenu placement="top start">
            <DropdownMenuLabel>{user?.email}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem onAction={handleSignOut} variant="destructive">
              <LogOut /> Sign out
            </DropdownMenuItem>
          </DropdownMenu>
        </DropdownMenuTrigger>
      </SidebarFooter>
    </Sidebar>
  )
}
