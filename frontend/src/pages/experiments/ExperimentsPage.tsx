import { Plus } from "lucide-react"
import { Link } from "@tanstack/react-router"

import { useEnvironmentContext } from "@/app/environment-context"
import { Badge } from "@/components/ui/badge"
import { buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useEnvironments } from "@/features/environments/hooks/useEnvironments"
import { useExperiments } from "@/features/experiments/hooks/useExperiments"
import { useFeatureFlags } from "@/features/featureflags/hooks/useFeatureFlags"

const statusVariant = {
  DRAFT: "secondary",
  RUNNING: "default",
  COMPLETED: "outline",
  CANCELLED: "destructive",
} as const

export function ExperimentsPage() {
  const { projectKey, isProjectsPending, projects } = useEnvironmentContext()
  const experiments = useExperiments(projectKey)
  const flags = useFeatureFlags(projectKey)
  const environments = useEnvironments(projectKey)
  const canEdit = projects.find((project) => project.key === projectKey)?.role !== "VIEWER"

  const flagById = new Map(flags.data?.map((flag) => [flag.id, flag]))
  const environmentById = new Map(environments.data?.map((env) => [env.id, env]))

  if (!isProjectsPending && !projectKey) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>No project selected</CardTitle>
          <CardDescription>Create or join a project to manage experiments.</CardDescription>
        </CardHeader>
      </Card>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Experiments</h1>
          <p className="text-sm text-muted-foreground">
            A/B experiments layered on a feature flag&apos;s variants, scoped to one environment.
          </p>
        </div>
        {canEdit && projectKey && (
          <Link className={buttonVariants()} to="/experiments/new">
            <Plus /> New experiment
          </Link>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All experiments</CardTitle>
          {experiments.isError && (
            <CardDescription className="text-destructive">
              Something went wrong loading experiments.
            </CardDescription>
          )}
        </CardHeader>
        <CardContent>
          {experiments.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : experiments.data && experiments.data.length > 0 ? (
            <Table aria-label="Experiments">
              <TableHeader>
                <TableHead isRowHeader>Key</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Flag</TableHead>
                <TableHead>Environment</TableHead>
                <TableHead>Status</TableHead>
              </TableHeader>
              <TableBody items={experiments.data}>
                {(experiment) => (
                  <TableRow id={experiment.id}>
                    <TableCell>
                      <Link
                        className="font-medium text-foreground no-underline hover:underline"
                        params={{ experimentKey: experiment.key }}
                        to="/experiments/$experimentKey"
                      >
                        {experiment.key}
                      </Link>
                    </TableCell>
                    <TableCell>{experiment.name}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {flagById.get(experiment.featureFlagId)?.key ?? "—"}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {environmentById.get(experiment.environmentId)?.key ?? "—"}
                    </TableCell>
                    <TableCell>
                      <Badge variant={statusVariant[experiment.status]}>{experiment.status}</Badge>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          ) : (
            <p className="py-8 text-center text-sm text-muted-foreground">
              No experiments yet. Create your first one to get started.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
