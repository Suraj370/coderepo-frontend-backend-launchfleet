import { Link } from "@tanstack/react-router"

import { useEnvironmentContext } from "@/app/environment-context"
import { Badge } from "@/components/ui/badge"
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
import { useApprovalRequests } from "@/features/approvals/hooks/useApprovalRequests"
import { approvalStatusVariant } from "@/features/approvals/types"
import { useEnvironments } from "@/features/environments/hooks/useEnvironments"
import { useFeatureFlags } from "@/features/featureflags/hooks/useFeatureFlags"
import { useProjectMembers } from "@/features/projects/hooks/useProjectMembers"

export function ApprovalsPage() {
  const { projectKey, isProjectsPending } = useEnvironmentContext()
  const approvals = useApprovalRequests(projectKey)
  const flags = useFeatureFlags(projectKey)
  const environments = useEnvironments(projectKey)
  const members = useProjectMembers(projectKey)

  const flagById = new Map(flags.data?.map((flag) => [flag.id, flag]))
  const environmentById = new Map(environments.data?.map((env) => [env.id, env]))
  const nameByUserId = new Map(members.data?.map((member) => [member.userId, member.name]))

  if (!isProjectsPending && !projectKey) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>No project selected</CardTitle>
          <CardDescription>Create or join a project to review approvals.</CardDescription>
        </CardHeader>
      </Card>
    )
  }

  const sorted = [...(approvals.data ?? [])].sort(
    (a, b) => new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime(),
  )

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Approvals</h1>
        <p className="text-sm text-muted-foreground">
          Two-person approval for proposed feature flag configuration changes.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All approval requests</CardTitle>
          {approvals.isError && (
            <CardDescription className="text-destructive">
              Something went wrong loading approval requests.
            </CardDescription>
          )}
        </CardHeader>
        <CardContent>
          {approvals.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : sorted.length > 0 ? (
            <Table aria-label="Approval requests">
              <TableHeader>
                <TableHead isRowHeader>Flag</TableHead>
                <TableHead>Environment</TableHead>
                <TableHead>Submitted by</TableHead>
                <TableHead>Submitted</TableHead>
                <TableHead>Status</TableHead>
              </TableHeader>
              <TableBody items={sorted}>
                {(request) => (
                  <TableRow id={request.id}>
                    <TableCell>
                      <Link
                        className="font-medium text-foreground no-underline hover:underline"
                        params={{ requestId: request.id }}
                        to="/approvals/$requestId"
                      >
                        {flagById.get(request.featureFlagId)?.key ?? "—"}
                      </Link>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {environmentById.get(request.environmentId)?.key ?? "—"}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {nameByUserId.get(request.submittedBy) ?? request.submittedBy}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {new Date(request.submittedAt).toLocaleString()}
                    </TableCell>
                    <TableCell>
                      <Badge variant={approvalStatusVariant[request.status]}>{request.status}</Badge>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          ) : (
            <p className="py-8 text-center text-sm text-muted-foreground">
              No approval requests yet. Submit a proposed change from a feature flag&apos;s targeting
              rules or rollout to get started.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
