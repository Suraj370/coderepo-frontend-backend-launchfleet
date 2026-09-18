import { useMemo, useState } from "react"
import { Plus, Search } from "lucide-react"
import { Link } from "@tanstack/react-router"

import { useEnvironmentContext } from "@/app/environment-context"
import { Badge } from "@/components/ui/badge"
import { buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useFeatureFlags } from "@/features/featureflags/hooks/useFeatureFlags"

export function FeatureFlagsPage() {
  const { projectKey, isProjectsPending, projects } = useEnvironmentContext()
  const flags = useFeatureFlags(projectKey)
  const canEdit = projects.find((project) => project.key === projectKey)?.role !== "VIEWER"
  const [search, setSearch] = useState("")

  const filteredFlags = useMemo(() => {
    const query = search.trim().toLowerCase()
    if (!query) return flags.data ?? []
    return (flags.data ?? []).filter(
      (flag) => flag.key.toLowerCase().includes(query) || flag.name.toLowerCase().includes(query),
    )
  }, [flags.data, search])

  if (!isProjectsPending && !projectKey) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>No project selected</CardTitle>
          <CardDescription>Create or join a project to manage feature flags.</CardDescription>
        </CardHeader>
      </Card>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Feature Flags</h1>
          <p className="text-sm text-muted-foreground">
            Boolean and multivariant flags for this project, with per-environment targeting.
          </p>
        </div>
        {canEdit && projectKey && (
          <Link className={buttonVariants()} to="/feature-flags/new">
            <Plus /> New flag
          </Link>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All flags</CardTitle>
          {flags.isError && (
            <CardDescription className="text-destructive">
              Something went wrong loading feature flags.
            </CardDescription>
          )}
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {!flags.isPending && flags.data && flags.data.length > 0 && (
            <InputGroup className="max-w-xs">
              <InputGroupInput
                aria-label="Search flags"
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search flags..."
                value={search}
              />
              <InputGroupAddon>
                <Search className="size-4 shrink-0 text-muted-foreground" />
              </InputGroupAddon>
            </InputGroup>
          )}

          {flags.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : filteredFlags.length > 0 ? (
            <Table aria-label="Feature flags">
              <TableHeader>
                <TableHead isRowHeader>Key</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Environments</TableHead>
              </TableHeader>
              <TableBody items={filteredFlags}>
                {(flag) => (
                  <TableRow id={flag.id}>
                    <TableCell>
                      <Link
                        className="font-medium text-foreground no-underline hover:underline"
                        params={{ flagKey: flag.key }}
                        to="/feature-flags/$flagKey"
                      >
                        {flag.key}
                      </Link>
                    </TableCell>
                    <TableCell>{flag.name}</TableCell>
                    <TableCell className="text-muted-foreground">{flag.type}</TableCell>
                    <TableCell>
                      <Badge variant={flag.status === "ACTIVE" ? "default" : "secondary"}>
                        {flag.status}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-1.5">
                        {flag.environments.map((env) => (
                          <span
                            className={
                              env.enabled
                                ? "size-2.5 rounded-full bg-primary"
                                : "size-2.5 rounded-full border border-muted-foreground/40"
                            }
                            key={env.environmentKey}
                            title={`${env.environmentKey}: ${env.enabled ? "enabled" : "disabled"}`}
                          />
                        ))}
                        {flag.environments.length === 0 && (
                          <span className="text-xs text-muted-foreground">No environments</span>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          ) : flags.data && flags.data.length > 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">
              No flags match &quot;{search}&quot;.
            </p>
          ) : (
            <p className="py-8 text-center text-sm text-muted-foreground">
              No feature flags yet. Create your first one to get started.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
