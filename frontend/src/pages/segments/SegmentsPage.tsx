import { Plus } from "lucide-react"
import { Link } from "@tanstack/react-router"

import { useEnvironmentContext } from "@/app/environment-context"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
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
import { useRetireSegment } from "@/features/segments/hooks/useSegmentMutations"
import { useSegments } from "@/features/segments/hooks/useSegments"
import type { Segment } from "@/features/segments/types"

function describeConditions(conditions: Segment["conditions"]): string {
  return conditions
    .map((condition) => {
      const subject = condition.type === "ATTRIBUTE" ? condition.attribute : "user key"
      return `${subject} ${condition.operator === "EQUALS" ? "=" : "in"} ${condition.values.join(", ")}`
    })
    .join(" AND ")
}

export function SegmentsPage() {
  const { projectKey, isProjectsPending, projects } = useEnvironmentContext()
  const segments = useSegments(projectKey)
  const retireSegment = useRetireSegment(projectKey ?? "")
  const canEdit = projects.find((project) => project.key === projectKey)?.role !== "VIEWER"

  if (!isProjectsPending && !projectKey) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>No project selected</CardTitle>
          <CardDescription>Create or join a project to manage segments.</CardDescription>
        </CardHeader>
      </Card>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Segments</h1>
          <p className="text-sm text-muted-foreground">
            Reusable, project-scoped user groups for targeting-rule SEGMENT_MATCH conditions.
          </p>
        </div>
        {canEdit && projectKey && (
          <Link className={buttonVariants()} to="/segments/new">
            <Plus /> New segment
          </Link>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All segments</CardTitle>
          {segments.isError && (
            <CardDescription className="text-destructive">
              Something went wrong loading segments.
            </CardDescription>
          )}
        </CardHeader>
        <CardContent>
          {segments.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : segments.data && segments.data.length > 0 ? (
            <Table aria-label="Segments">
              <TableHeader>
                <TableHead isRowHeader>Key</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Conditions</TableHead>
                <TableHead>{""}</TableHead>
              </TableHeader>
              <TableBody items={segments.data}>
                {(segment) => (
                  <TableRow id={segment.id}>
                    <TableCell>{segment.key}</TableCell>
                    <TableCell>{segment.name}</TableCell>
                    <TableCell>
                      <Badge variant={segment.status === "ACTIVE" ? "default" : "secondary"}>
                        {segment.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="max-w-md text-muted-foreground">
                      {describeConditions(segment.conditions)}
                    </TableCell>
                    <TableCell>
                      {canEdit && (
                        <div className="flex gap-1">
                          <Link
                            className={buttonVariants({ size: "sm", variant: "outline" })}
                            params={{ segmentKey: segment.key }}
                            to="/segments/$segmentKey/edit"
                          >
                            Edit
                          </Link>
                          {segment.status === "ACTIVE" && (
                            <Button
                              isDisabled={retireSegment.isPending}
                              onPress={() => retireSegment.mutate(segment.key)}
                              size="sm"
                              variant="destructive"
                            >
                              Retire
                            </Button>
                          )}
                        </div>
                      )}
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          ) : (
            <p className="py-8 text-center text-sm text-muted-foreground">
              No segments yet. Create your first one to get started.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
