import {
  ArrowUpRight,
  CheckCircle2,
  FlaskConical,
  Layers,
  Network,
  PlayCircle,
  Send,
  ShieldCheck,
  ToggleRight,
  UsersRound,
  XCircle,
  type LucideIcon,
} from "lucide-react"
import { Link, useNavigate } from "@tanstack/react-router"
import { Bar, BarChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis } from "recharts"

import { useEnvironmentContext } from "@/app/environment-context"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { type ActivityAction, type ActivityEntry } from "@/features/dashboard/api"
import { useActivity } from "@/features/dashboard/hooks/useActivity"
import { useDashboardSummary } from "@/features/dashboard/hooks/useDashboardSummary"
import { useFlagEvaluationSummary } from "@/features/dashboard/hooks/useFlagEvaluationSummary"

const quickActions: { label: string; to: string; icon: LucideIcon }[] = [
  { label: "Create Feature Flag", to: "/feature-flags", icon: ToggleRight },
  { label: "Create Experiment", to: "/experiments", icon: FlaskConical },
  { label: "Manage Segments", to: "/segments", icon: UsersRound },
  { label: "View Approvals", to: "/approvals", icon: ShieldCheck },
]

const activityMeta: Record<ActivityAction, { icon: LucideIcon; label: string; color: string }> = {
  FLAG_CREATED: { icon: ToggleRight, label: "created flag", color: "#0f7a52" },
  FLAG_RETIRED: { icon: XCircle, label: "retired flag", color: "#94a3b8" },
  ENVIRONMENT_CREATED: { icon: Network, label: "created environment", color: "#0f7a52" },
  ENVIRONMENT_RETIRED: { icon: XCircle, label: "retired environment", color: "#94a3b8" },
  SEGMENT_CREATED: { icon: UsersRound, label: "created segment", color: "#0f7a52" },
  SEGMENT_RETIRED: { icon: XCircle, label: "retired segment", color: "#94a3b8" },
  EXPERIMENT_CREATED: { icon: FlaskConical, label: "created experiment", color: "#0f7a52" },
  EXPERIMENT_STARTED: { icon: PlayCircle, label: "started experiment", color: "#2563eb" },
  EXPERIMENT_COMPLETED: { icon: CheckCircle2, label: "completed experiment", color: "#0f7a52" },
  APPROVAL_SUBMITTED: { icon: Send, label: "requested approval for", color: "#d97706" },
  APPROVAL_APPROVED: { icon: CheckCircle2, label: "approved", color: "#0f7a52" },
  APPROVAL_REJECTED: { icon: XCircle, label: "rejected", color: "#dc2626" },
}

function relativeTime(iso: string): string {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  const units: [number, string][] = [
    [60, "second"],
    [60, "minute"],
    [24, "hour"],
    [7, "day"],
    [4.345, "week"],
    [12, "month"],
    [Number.POSITIVE_INFINITY, "year"],
  ]

  let value = seconds
  for (const [size, unit] of units) {
    if (value < size) {
      const rounded = Math.floor(value)
      return rounded <= 1 ? `1 ${unit} ago` : `${rounded} ${unit}s ago`
    }
    value /= size
  }

  return "just now"
}

export function DashboardPage() {
  const navigate = useNavigate()
  const { projectKey, isProjectsPending } = useEnvironmentContext()
  const summary = useDashboardSummary(projectKey)
  const activity = useActivity(projectKey)
  const evaluationSummary = useFlagEvaluationSummary(projectKey)

  if (!isProjectsPending && !projectKey) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>No projects yet</CardTitle>
          <CardDescription>
            You&apos;re not a member of any project. Create one to start tracking flags,
            environments, and experiments.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button onPress={() => navigate({ to: "/projects" })}>Create a project</Button>
        </CardContent>
      </Card>
    )
  }

  const donutData = [
    { name: "Active", value: summary.flagsActive, color: "#0f7a52" },
    { name: "Retired", value: summary.flagsRetired, color: "#cbd5c9" },
  ]

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Here&apos;s what&apos;s happening with your feature flags.
        </p>
      </div>

      {summary.isError && (
        <p className="text-sm text-destructive">
          Something went wrong loading the dashboard summary.
        </p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          detail={`${summary.flagsActive} active · ${summary.flagsRetired} retired`}
          icon={ToggleRight}
          isPending={summary.isPending}
          title="Feature Flags"
          value={summary.flagsTotal}
        />
        <StatCard
          detail={summary.environmentNames.join(", ") || "No environments yet"}
          icon={Network}
          isPending={summary.isPending}
          title="Environments"
          value={summary.environmentsTotal}
        />
        <StatCard
          detail={`${summary.segmentsActive} active · ${summary.segmentsRetired} retired`}
          icon={Layers}
          isPending={summary.isPending}
          title="Segments"
          value={summary.segmentsTotal}
        />
        <StatCard
          detail={`${summary.experimentsRunning} running · ${summary.experimentsCompleted} completed`}
          icon={FlaskConical}
          isPending={summary.isPending}
          title="Experiments"
          value={summary.experimentsTotal}
        />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1.5fr_1fr]">
        <Card>
          <CardHeader>
            <CardTitle>Flag Evaluations</CardTitle>
            <CardDescription>
              {evaluationSummary.isPending || !evaluationSummary.data
                ? "Last 7 days, from the SDK evaluation endpoint."
                : `${evaluationSummary.data.totalLast7Days} in the last 7 days (${
                    evaluationSummary.data.percentChangeVsPriorPeriod >= 0 ? "+" : ""
                  }${Math.round(evaluationSummary.data.percentChangeVsPriorPeriod)}% vs. prior 7 days)`}
            </CardDescription>
          </CardHeader>
          <CardContent className="h-48">
            {evaluationSummary.isPending ? (
              <Skeleton className="h-full w-full" />
            ) : evaluationSummary.isError ? (
              <p className="flex h-full items-center justify-center text-sm text-destructive">
                Something went wrong loading evaluation data.
              </p>
            ) : evaluationSummary.data.totalLast7Days === 0 ? (
              <p className="flex h-full items-center justify-center text-sm text-muted-foreground">
                No SDK evaluations recorded yet.
              </p>
            ) : (
              <ResponsiveContainer height="100%" width="100%">
                <BarChart data={evaluationSummary.data.byDay}>
                  <XAxis
                    axisLine={false}
                    dataKey="date"
                    fontSize={12}
                    tickFormatter={(date: string) =>
                      new Date(date).toLocaleDateString(undefined, { weekday: "short" })
                    }
                    tickLine={false}
                  />
                  <Tooltip
                    labelFormatter={(date) => new Date(String(date)).toLocaleDateString()}
                  />
                  <Bar dataKey="count" fill="#0f7a52" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Flag Status</CardTitle>
          </CardHeader>
          <CardContent>
            {summary.isPending ? (
              <Skeleton className="mx-auto h-36 w-36 rounded-full" />
            ) : summary.flagsTotal === 0 ? (
              <p className="py-12 text-center text-sm text-muted-foreground">No flags yet.</p>
            ) : (
              <div className="flex items-center gap-4">
                <div className="h-36 w-36 shrink-0">
                  <ResponsiveContainer height="100%" width="100%">
                    <PieChart>
                      <Pie
                        data={donutData}
                        dataKey="value"
                        innerRadius={44}
                        outerRadius={64}
                        paddingAngle={2}
                      >
                        {donutData.map((entry) => (
                          <Cell fill={entry.color} key={entry.name} />
                        ))}
                      </Pie>
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <ul className="flex flex-col gap-1.5 text-sm">
                  {donutData.map((entry) => (
                    <li className="flex items-center gap-2" key={entry.name}>
                      <span
                        className="h-2 w-2 rounded-full"
                        style={{ backgroundColor: entry.color }}
                      />
                      {entry.name}
                      <span className="ml-auto font-medium">{entry.value}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1.5fr_1fr]">
        <Card>
          <CardHeader>
            <CardTitle>Recent Activity</CardTitle>
            <CardDescription>The last 50 notable actions in this project.</CardDescription>
          </CardHeader>
          <CardContent>
            {activity.isPending ? (
              <div className="flex flex-col gap-3">
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
              </div>
            ) : activity.data && activity.data.length > 0 ? (
              <ul className="flex flex-col gap-3">
                {activity.data.slice(0, 6).map((entry) => (
                  <ActivityRow entry={entry} key={entry.id} />
                ))}
              </ul>
            ) : (
              <p className="py-8 text-center text-sm text-muted-foreground">No activity yet.</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Quick Actions</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-1">
            {quickActions.map(({ label, to, icon: Icon }) => (
              <Link
                className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm no-underline transition-colors hover:bg-accent hover:text-accent-foreground"
                key={label}
                to={to}
              >
                <Icon size={16} /> {label}
                <ArrowUpRight className="ml-auto text-muted-foreground" size={14} />
              </Link>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function ActivityRow({ entry }: { entry: ActivityEntry }) {
  const meta = activityMeta[entry.action]
  const Icon = meta.icon

  return (
    <li className="flex items-start gap-3">
      <span
        className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full"
        style={{ backgroundColor: `${meta.color}1a`, color: meta.color }}
      >
        <Icon size={14} />
      </span>
      <div className="flex min-w-0 flex-1 flex-col text-sm">
        <span className="truncate">
          <span className="font-medium">{entry.actorName}</span> {meta.label}{" "}
          <span className="font-medium">{entry.subjectKey}</span>
        </span>
        <span className="text-xs text-muted-foreground">{relativeTime(entry.occurredAt)}</span>
      </div>
    </li>
  )
}

function StatCard({
  title,
  value,
  detail,
  icon: Icon,
  isPending,
}: {
  title: string
  value: number
  detail: string
  icon: LucideIcon
  isPending: boolean
}) {
  return (
    <Card className="relative overflow-hidden">
      <Icon
        className="pointer-events-none absolute -right-3 -top-3 text-accent-foreground/10"
        size={72}
        strokeWidth={1.25}
      />
      <CardHeader>
        <CardDescription>{title}</CardDescription>
        <CardTitle className="text-2xl">
          {isPending ? <Skeleton className="h-7 w-10" /> : value}
        </CardTitle>
      </CardHeader>
      <CardContent className="text-xs text-muted-foreground">
        {isPending ? <Skeleton className="h-4 w-32" /> : detail}
      </CardContent>
    </Card>
  )
}
