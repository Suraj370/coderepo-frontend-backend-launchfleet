import { createContext, useContext, useState, type ReactNode } from "react"

import { useProjects } from "@/features/projects/hooks/useProjects"
import type { ProjectMembership } from "@/features/projects/types"

interface EnvironmentContextValue {
  projectKey: string | null
  projects: ProjectMembership[]
  isProjectsPending: boolean
  setProjectKey: (key: string) => void
  environmentKey: string | null
  setEnvironmentKey: (key: string) => void
}

const EnvironmentContext = createContext<EnvironmentContextValue | undefined>(undefined)

export function EnvironmentProvider({ children }: { children: ReactNode }) {
  const projects = useProjects()
  const [manualProjectKey, setManualProjectKey] = useState<string | null>(null)
  const [environmentKey, setEnvironmentKey] = useState<string | null>(null)

  // Fall back to the caller's first membership whenever the manual pick isn't
  // (or is no longer) one of their actual projects - derived each render
  // instead of synced via effect, so there's nothing to fall out of sync.
  const projectKey =
    manualProjectKey && projects.data?.some((project) => project.key === manualProjectKey)
      ? manualProjectKey
      : (projects.data?.[0]?.key ?? null)

  function setProjectKey(key: string) {
    setManualProjectKey(key)
    setEnvironmentKey(null)
  }

  return (
    <EnvironmentContext.Provider
      value={{
        projectKey,
        projects: projects.data ?? [],
        isProjectsPending: projects.isPending,
        setProjectKey,
        environmentKey,
        setEnvironmentKey,
      }}
    >
      {children}
    </EnvironmentContext.Provider>
  )
}

export function useEnvironmentContext() {
  const context = useContext(EnvironmentContext)

  if (!context) {
    throw new Error("useEnvironmentContext must be used within an EnvironmentProvider")
  }

  return context
}
