import { useState } from "react"
import { Link, useParams } from "@tanstack/react-router"

import { useEnvironmentContext } from "@/app/environment-context"
import { getApiErrorMessage } from "@/api/errors"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Textarea } from "@/components/ui/textarea"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useApprovalRequest } from "@/features/approvals/hooks/useApprovalRequest"
import {
  useApproveApprovalRequest,
  useCancelApprovalRequest,
  useRejectApprovalRequest,
  useScheduleApprovalRequest,
} from "@/features/approvals/hooks/useApprovalMutations"
import { approvalStatusVariant } from "@/features/approvals/types"
import type { ApprovalRequest, ApprovalTargetingRule } from "@/features/approvals/types"
import { useSession } from "@/features/auth/hooks/useSession"
import { useEnvironments } from "@/features/environments/hooks/useEnvironments"
import { useFeatureFlags } from "@/features/featureflags/hooks/useFeatureFlags"
import type { FeatureFlagVariant } from "@/features/featureflags/types"
import { useProjectMembers } from "@/features/projects/hooks/useProjectMembers"
import { useSegments } from "@/features/segments/hooks/useSegments"

function describeConditions(
  conditions: ApprovalTargetingRule["conditions"],
  segmentNameById: Map<string, string>,
): string {
  return conditions
    .map((condition) => {
      if (condition.type === "SEGMENT_MATCH") {
        const segmentId = condition.values[0]
        return `segment = ${segmentNameById.get(segmentId ?? "") ?? segmentId}`
      }

      const subject = condition.type === "ATTRIBUTE" ? condition.attribute : "user key"

      return `${subject} ${condition.operator === "EQUALS" ? "=" : "in"} ${condition.values.join(", ")}`
    })
    .join(" AND ")
}

export function ApprovalDetailPage() {
  const { requestId } = useParams({ from: "/_app/approvals_/$requestId" })
  const { projectKey, projects } = useEnvironmentContext()
  const request = useApprovalRequest(projectKey, requestId)
  const flags = useFeatureFlags(projectKey)
  const environments = useEnvironments(projectKey)
  const members = useProjectMembers(projectKey)
  const session = useSession()

  if (request.isPending || flags.isPending || environments.isPending) {
    return (
      <div className="flex flex-col gap-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-48 w-full" />
      </div>
    )
  }

  if (request.isError || !request.data || !projectKey) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Approval request not found</CardTitle>
          <CardDescription>Something went wrong loading this approval request.</CardDescription>
        </CardHeader>
        <CardContent>
          <Link to="/approvals">Back to approvals</Link>
        </CardContent>
      </Card>
    )
  }

  const data = request.data
  const flag = flags.data?.find((f) => f.id === data.featureFlagId) ?? null
  const environment = environments.data?.find((e) => e.id === data.environmentId) ?? null
  const role = projects.find((project) => project.key === projectKey)?.role
  const isAdmin = role === "ADMIN"
  const currentUserId = session.data?.user?.id
  const nameByUserId = new Map(members.data?.map((member) => [member.userId, member.name]))

  function nameOf(userId: string | null) {
    if (!userId) return "—"
    return nameByUserId.get(userId) ?? userId
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <Link className="w-fit text-sm text-muted-foreground no-underline hover:underline" to="/approvals">
          ← Approvals
        </Link>
        <div className="flex items-center gap-2">
          <h1 className="text-2xl font-semibold">{flag?.key ?? "Unknown flag"}</h1>
          <Badge variant={approvalStatusVariant[data.status]}>{data.status}</Badge>
        </div>
        <div className="flex gap-2 text-sm text-muted-foreground">
          <span>
            Environment: <span className="font-medium text-foreground">{environment?.key ?? "—"}</span>
          </span>
          <span>·</span>
          <span>
            Submitted by <span className="font-medium text-foreground">{nameOf(data.submittedBy)}</span> on{" "}
            {new Date(data.submittedAt).toLocaleString()}
          </span>
        </div>
      </div>

      {!flag ? (
        <Card>
          <CardContent className="py-8 text-center text-sm text-muted-foreground">
            Could not find the feature flag this approval request references.
          </CardContent>
        </Card>
      ) : (
        <ProposedChangesCard flag={flag} request={data} />
      )}

      <ReviewCard nameOf={nameOf} request={data} />

      {data.status === "PENDING" && isAdmin && currentUserId !== data.submittedBy && (
        <ReviewActionsCard projectKey={projectKey} requestId={data.id} />
      )}

      <CancelCard
        currentUserId={currentUserId}
        isAdmin={isAdmin}
        projectKey={projectKey}
        request={data}
      />
    </div>
  )
}

function ProposedChangesCard({
  flag,
  request,
}: {
  flag: { variants: FeatureFlagVariant[] }
  request: ApprovalRequest
}) {
  const { projectKey } = useEnvironmentContext()
  const segments = useSegments(projectKey)
  const segmentNameById = new Map(segments.data?.map((segment) => [segment.id, segment.name]))
  const config = request.proposedConfig
  const sortedRules = [...config.targetingRules].sort((a, b) => a.priority - b.priority)

  function variantName(variantId: string) {
    return flag.variants.find((v) => v.id === variantId)?.name ?? variantId
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Proposed changes</CardTitle>
        <CardDescription>
          Immutable snapshot captured at submission time, based on config version{" "}
          {request.baseConfigVersion}.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        <div className="flex flex-col gap-1 text-sm">
          <span className="text-muted-foreground">Enabled</span>
          <span className="font-medium">{config.enabled ? "Yes" : "No"}</span>
        </div>
        <div className="flex flex-col gap-1 text-sm">
          <span className="text-muted-foreground">Default variant</span>
          <span className="font-medium">{variantName(config.defaultVariantId)}</span>
        </div>

        <div className="flex flex-col gap-2">
          <span className="text-sm text-muted-foreground">Targeting rules</span>
          {sortedRules.length === 0 ? (
            <p className="py-4 text-center text-sm text-muted-foreground">No targeting rules.</p>
          ) : (
            <Table aria-label="Proposed targeting rules">
              <TableHeader>
                <TableHead isRowHeader>Priority</TableHead>
                <TableHead>Conditions</TableHead>
                <TableHead>Serves</TableHead>
              </TableHeader>
              <TableBody items={sortedRules}>
                {(rule) => (
                  <TableRow id={rule.id}>
                    <TableCell>{rule.priority}</TableCell>
                    <TableCell className="max-w-md text-muted-foreground">
                      {describeConditions(rule.conditions, segmentNameById)}
                    </TableCell>
                    <TableCell>{variantName(rule.variantId)}</TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          )}
        </div>

        <div className="flex flex-col gap-2">
          <span className="text-sm text-muted-foreground">Rollout</span>
          {config.rollout ? (
            config.rollout.allocations.map((allocation) => (
              <div className="flex items-center gap-2 text-sm" key={allocation.variantId}>
                <span className="w-32 truncate font-medium">{variantName(allocation.variantId)}</span>
                <span className="text-muted-foreground">{(allocation.percentage / 100).toFixed(0)}%</span>
              </div>
            ))
          ) : (
            <p className="py-4 text-center text-sm text-muted-foreground">No rollout.</p>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

function ReviewCard({
  request,
  nameOf,
}: {
  request: ApprovalRequest
  nameOf: (userId: string | null) => string
}) {
  if (
    !request.reviewedBy &&
    !request.scheduledAt &&
    !request.cancelledAt &&
    request.appliedVersion === null
  ) {
    return null
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Review history</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-2 text-sm">
        {request.reviewedBy && (
          <p>
            Reviewed by <span className="font-medium">{nameOf(request.reviewedBy)}</span> on{" "}
            {request.reviewedAt && new Date(request.reviewedAt).toLocaleString()}
          </p>
        )}
        {request.approvalComment && (
          <p className="text-muted-foreground">Comment: {request.approvalComment}</p>
        )}
        {request.rejectionComment && (
          <p className="text-muted-foreground">Reason: {request.rejectionComment}</p>
        )}
        {request.scheduledAt && (
          <p>
            Scheduled for <span className="font-medium">{new Date(request.scheduledAt).toLocaleString()}</span>
          </p>
        )}
        {request.appliedVersion !== null && (
          <p>
            Applied as config version <span className="font-medium">{request.appliedVersion}</span>
          </p>
        )}
        {request.cancelledAt && (
          <p>
            Cancelled by <span className="font-medium">{nameOf(request.cancelledBy)}</span> on{" "}
            {new Date(request.cancelledAt).toLocaleString()} ({request.cancellationReason})
          </p>
        )}
      </CardContent>
    </Card>
  )
}

function ReviewActionsCard({ projectKey, requestId }: { projectKey: string; requestId: string }) {
  const approve = useApproveApprovalRequest(projectKey, requestId)
  const reject = useRejectApprovalRequest(projectKey, requestId)
  const schedule = useScheduleApprovalRequest(projectKey, requestId)
  const [approvalComment, setApprovalComment] = useState("")
  const [rejectionComment, setRejectionComment] = useState("")
  const [scheduledAt, setScheduledAt] = useState("")
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  async function handleApprove() {
    setErrorMessage(null)
    try {
      await approve.mutateAsync({ approvalComment: approvalComment || undefined })
    } catch (error) {
      setErrorMessage(await getApiErrorMessage(error))
    }
  }

  async function handleReject() {
    setErrorMessage(null)
    try {
      await reject.mutateAsync({ rejectionComment })
    } catch (error) {
      setErrorMessage(await getApiErrorMessage(error))
    }
  }

  async function handleSchedule() {
    setErrorMessage(null)
    try {
      await schedule.mutateAsync({
        approvalComment: approvalComment || undefined,
        scheduledAt: new Date(scheduledAt).toISOString(),
      })
    } catch (error) {
      setErrorMessage(await getApiErrorMessage(error))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Review</CardTitle>
        <CardDescription>Requires ADMIN. You cannot review your own submission.</CardDescription>
      </CardHeader>
      <CardContent>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="approval-comment">Comment (optional, used for approve/schedule)</FieldLabel>
            <Textarea
              id="approval-comment"
              onChange={(event) => setApprovalComment(event.target.value)}
              value={approvalComment}
            />
          </Field>

          <div className="flex flex-wrap items-end gap-2">
            <Button isDisabled={approve.isPending} onPress={handleApprove}>
              {approve.isPending ? "Approving..." : "Approve now"}
            </Button>

            <Field className="w-56">
              <FieldLabel htmlFor="scheduled-at">Schedule for</FieldLabel>
              <Input
                id="scheduled-at"
                onChange={(event) => setScheduledAt(event.target.value)}
                type="datetime-local"
                value={scheduledAt}
              />
            </Field>
            <Button
              isDisabled={schedule.isPending || !scheduledAt}
              onPress={handleSchedule}
              variant="outline"
            >
              {schedule.isPending ? "Scheduling..." : "Schedule"}
            </Button>
          </div>

          <Field>
            <FieldLabel htmlFor="rejection-comment">Rejection reason (required to reject)</FieldLabel>
            <Textarea
              id="rejection-comment"
              onChange={(event) => setRejectionComment(event.target.value)}
              value={rejectionComment}
            />
          </Field>
          <Button
            className="w-fit"
            isDisabled={reject.isPending || !rejectionComment.trim()}
            onPress={handleReject}
            variant="destructive"
          >
            {reject.isPending ? "Rejecting..." : "Reject"}
          </Button>

          {errorMessage && <p className="text-sm text-destructive">{errorMessage}</p>}
        </FieldGroup>
      </CardContent>
    </Card>
  )
}

function CancelCard({
  request,
  projectKey,
  isAdmin,
  currentUserId,
}: {
  request: ApprovalRequest
  projectKey: string
  isAdmin: boolean
  currentUserId: string | undefined
}) {
  const cancel = useCancelApprovalRequest(projectKey, request.id)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const canCancel =
    (request.status === "PENDING" && (isAdmin || currentUserId === request.submittedBy)) ||
    (request.status === "SCHEDULED" && isAdmin)

  if (!canCancel) return null

  async function handleCancel() {
    setErrorMessage(null)
    try {
      await cancel.mutateAsync()
    } catch (error) {
      setErrorMessage(await getApiErrorMessage(error))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Cancel</CardTitle>
        <CardDescription>
          {request.status === "SCHEDULED"
            ? "Cancel this scheduled change before it is applied."
            : "Withdraw this request before it is reviewed."}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-2">
        <Button
          className="w-fit"
          isDisabled={cancel.isPending}
          onPress={handleCancel}
          variant="outline"
        >
          {cancel.isPending ? "Cancelling..." : "Cancel request"}
        </Button>
        {errorMessage && <p className="text-sm text-destructive">{errorMessage}</p>}
      </CardContent>
    </Card>
  )
}
