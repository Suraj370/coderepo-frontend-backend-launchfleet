import { Network, Plus } from "lucide-react"
import { Link } from "@tanstack/react-router"

import { useEnvironmentContext } from "@/app/environment-context"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useRetireEnvironment } from "@/features/environments/hooks/useEnvironmentMutations"
import { useEnvironments } from "@/features/environments/hooks/useEnvironments"
import { useFeatureFlags } from "@/features/featureflags/hooks/useFeatureFlags"

export function EnvironmentsPage() {
  const { projectKey, isProjectsPending, projects } = useEnvironmentContext()
  const environments = useEnvironments(projectKey)
  const flags = useFeatureFlags(projectKey)
  const retireEnvironment = useRetireEnvironment(projectKey ?? "")
  const canEdit = projects.find((project) => project.key === projectKey)?.role !== "VIEWER"

  function enabledFlagCount(environmentKey: string) {
    return (flags.data ?? []).filter((flag) =>
      flag.environments.some((env) => env.environmentKey === environmentKey && env.enabled),
    ).length
  }

  if (!isProjectsPending && !projectKey) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>No project selected</CardTitle>
          <CardDescription>Create or join a project to manage environments.</CardDescription>
        </CardHeader>
      </Card>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Environments</h1>
          <p className="text-sm text-muted-foreground">
            Environments for this project (e.g. production/staging). Every flag gets one
            configuration per environment.
          </p>
        </div>
        {canEdit && projectKey && (
          <Link className={buttonVariants()} to="/environments/new">
            <Plus /> New environment
          </Link>
        )}
      </div>

      {environments.isError && (
        <p className="text-sm text-destructive">Something went wrong loading environments.</p>
      )}

      {environments.isPending ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-32 w-full" />
        </div>
      ) : environments.data && environments.data.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {environments.data.map((environment) => (
            <Card className="relative overflow-hidden" key={environment.id}>
              <Network
                className="pointer-events-none absolute -right-3 -top-3 text-accent-foreground/10"
                size={72}
                strokeWidth={1.25}
              />
              <CardHeader>
                <div className="flex items-center gap-2">
                  <CardTitle>{environment.name}</CardTitle>
                  <Badge variant={environment.status === "ACTIVE" ? "default" : "secondary"}>
                    {environment.status}
                  </Badge>
                </div>
                <CardDescription>{environment.key}</CardDescription>
              </CardHeader>
              <CardContent className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">
                  {enabledFlagCount(environment.key)} flag
                  {enabledFlagCount(environment.key) === 1 ? "" : "s"} enabled
                </span>
                {canEdit && environment.status === "ACTIVE" && (
                  <Button
                    isDisabled={retireEnvironment.isPending}
                    onPress={() => retireEnvironment.mutate(environment.key)}
                    size="sm"
                    variant="destructive"
                  >
                    Retire
                  </Button>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      ) : (
        <Card>
          <CardContent className="py-8 text-center text-sm text-muted-foreground">
            No environments yet. Create your first one to get started.
          </CardContent>
        </Card>
      )}
    </div>
  )
}
