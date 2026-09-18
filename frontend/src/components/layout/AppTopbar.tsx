import { useEffect } from "react"

import { useEnvironmentContext } from "@/app/environment-context"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { SidebarTrigger } from "@/components/ui/sidebar"
import { Skeleton } from "@/components/ui/skeleton"
import { useEnvironments } from "@/features/environments/hooks/useEnvironments"

export function AppTopbar() {
  const {
    projectKey,
    projects,
    isProjectsPending,
    setProjectKey,
    environmentKey,
    setEnvironmentKey,
  } = useEnvironmentContext()
  const environments = useEnvironments(projectKey)

  useEffect(() => {
    if (!environmentKey && environments.data && environments.data.length > 0) {
      setEnvironmentKey(environments.data[0].key)
    }
  }, [environmentKey, environments.data, setEnvironmentKey])

  return (
    <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center gap-3 border-b bg-card/60 px-4 backdrop-blur-sm">
      <SidebarTrigger />

      {isProjectsPending ? (
        <Skeleton className="h-8 w-32" />
      ) : projectKey ? (
        <Select
          aria-label="Project"
          selectedKey={projectKey}
          onSelectionChange={(key) => setProjectKey(String(key))}
        >
          <SelectTrigger className="w-32">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {projects.map((project) => (
              <SelectItem id={project.key} key={project.key}>
                {project.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      ) : (
        <span className="text-sm text-muted-foreground">No projects</span>
      )}

      {/* {projectKey &&
        (environments.isPending ? (
          <Skeleton className="h-8 w-36" />
        ) : (
          <Select
            aria-label="Environment"
            selectedKey={environmentKey}
            onSelectionChange={(key) => setEnvironmentKey(String(key))}
          >
            <SelectTrigger className="w-36">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {environments.data?.map((environment) => (
                <SelectItem id={environment.key} key={environment.key}>
                  {environment.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ))} */}

    </header>
  )
}
