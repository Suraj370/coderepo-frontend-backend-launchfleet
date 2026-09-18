import { type FormEvent, useState } from "react"
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
import { useEnvironments } from "@/features/environments/hooks/useEnvironments"
import { useExperiment } from "@/features/experiments/hooks/useExperiment"
import { useExperimentMetrics } from "@/features/experiments/hooks/useExperimentMetrics"
import {
  useStartExperiment,
  useStopExperiment,
  useUpdateExperiment,
} from "@/features/experiments/hooks/useExperimentMutations"
import type { Allocation, Experiment } from "@/features/experiments/types"
import { useFeatureFlags } from "@/features/featureflags/hooks/useFeatureFlags"
import type { FeatureFlagVariant } from "@/features/featureflags/types"
import { Slider, SliderTrack } from "@/components/ui/slider"

const statusVariant = {
  DRAFT: "secondary",
  RUNNING: "default",
  COMPLETED: "outline",
  CANCELLED: "destructive",
} as const

export function ExperimentDetailPage() {
  const { experimentKey } = useParams({ from: "/_app/experiments_/$experimentKey" })
  const { projectKey } = useEnvironmentContext()
  const experiment = useExperiment(projectKey, experimentKey)
  const flags = useFeatureFlags(projectKey)
  const environments = useEnvironments(projectKey)

  if (experiment.isPending || flags.isPending || environments.isPending) {
    return (
      <div className="flex flex-col gap-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-48 w-full" />
      </div>
    )
  }

  if (experiment.isError || !experiment.data || !projectKey) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Experiment not found</CardTitle>
          <CardDescription>Something went wrong loading this experiment.</CardDescription>
        </CardHeader>
        <CardContent>
          <Link to="/experiments">Back to experiments</Link>
        </CardContent>
      </Card>
    )
  }

  const flag = flags.data?.find((f) => f.id === experiment.data.featureFlagId) ?? null
  const environment = environments.data?.find((e) => e.id === experiment.data.environmentId) ?? null

  return (
    <div className="flex flex-col gap-6">
      <Header experiment={experiment.data} projectKey={projectKey} />

      <div className="flex gap-2 text-sm text-muted-foreground">
        <span>
          Flag: <span className="font-medium text-foreground">{flag?.key ?? "—"}</span>
        </span>
        <span>·</span>
        <span>
          Environment: <span className="font-medium text-foreground">{environment?.key ?? "—"}</span>
        </span>
      </div>

      {!flag ? (
        <Card>
          <CardContent className="py-8 text-center text-sm text-muted-foreground">
            Could not find the feature flag this experiment references.
          </CardContent>
        </Card>
      ) : experiment.data.status === "DRAFT" ? (
        <ConfigurationCard experiment={experiment.data} projectKey={projectKey} variants={flag.variants} />
      ) : (
        <>
          <SummaryCard experiment={experiment.data} variants={flag.variants} />
          <MetricsCard
            experimentKey={experiment.data.key}
            projectKey={projectKey}
            variants={flag.variants}
          />
        </>
      )}
    </div>
  )
}

function Header({ experiment, projectKey }: { experiment: Experiment; projectKey: string }) {
  const startExperiment = useStartExperiment(projectKey, experiment.key)
  const stopExperiment = useStopExperiment(projectKey, experiment.key)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const canStart =
    experiment.status === "DRAFT" &&
    experiment.allocation !== null &&
    Boolean(experiment.conversionEventName)

  async function handleStart() {
    setErrorMessage(null)
    try {
      await startExperiment.mutateAsync()
    } catch (error) {
      setErrorMessage(await getApiErrorMessage(error))
    }
  }

  async function handleStop() {
    setErrorMessage(null)
    try {
      await stopExperiment.mutateAsync()
    } catch (error) {
      setErrorMessage(await getApiErrorMessage(error))
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <Link className="w-fit text-sm text-muted-foreground no-underline hover:underline" to="/experiments">
        ← Experiments
      </Link>
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-semibold">{experiment.name}</h1>
            <Badge variant={statusVariant[experiment.status]}>{experiment.status}</Badge>
          </div>
          <p className="text-sm text-muted-foreground">{experiment.key}</p>
          {experiment.description && <p className="mt-1 text-sm">{experiment.description}</p>}
        </div>
        {experiment.status === "DRAFT" && (
          <Button isDisabled={!canStart || startExperiment.isPending} onPress={handleStart}>
            {startExperiment.isPending ? "Starting..." : "Start experiment"}
          </Button>
        )}
        {experiment.status === "RUNNING" && (
          <Button isDisabled={stopExperiment.isPending} onPress={handleStop} variant="destructive">
            {stopExperiment.isPending ? "Stopping..." : "Stop experiment"}
          </Button>
        )}
      </div>
      {errorMessage && <p className="text-sm text-destructive">{errorMessage}</p>}
    </div>
  )
}

function ConfigurationCard({
  experiment,
  projectKey,
  variants,
}: {
  experiment: Experiment
  projectKey: string
  variants: FeatureFlagVariant[]
}) {
  const updateExperiment = useUpdateExperiment(projectKey, experiment.key)
  const [name, setName] = useState(experiment.name)
  const [description, setDescription] = useState(experiment.description ?? "")
  const [conversionEventName, setConversionEventName] = useState(experiment.conversionEventName ?? "")
  const [allocations, setAllocations] = useState<Allocation[]>(
    experiment.allocation?.allocations ?? variants.map((v) => ({ variantId: v.id, percentage: 0 })),
  )
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const total = allocations.reduce((sum, allocation) => sum + allocation.percentage, 0)

  function updatePercentage(variantId: string, percentage: number) {
    setAllocations((current) =>
      current.map((allocation) =>
        allocation.variantId === variantId ? { ...allocation, percentage } : allocation,
      ),
    )
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrorMessage(null)
    try {
      await updateExperiment.mutateAsync({
        name,
        description: description || undefined,
        allocation: allocations,
        conversionEventName: conversionEventName || undefined,
      })
    } catch (error) {
      setErrorMessage(await getApiErrorMessage(error))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Configuration</CardTitle>
        <CardDescription>
          Frozen once started. Allocation must sum to exactly 10000 basis points (100%).
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="experiment-config-name">Name</FieldLabel>
              <Input
                id="experiment-config-name"
                onChange={(event) => setName(event.target.value)}
                required
                type="text"
                value={name}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="experiment-config-description">Description</FieldLabel>
              <Textarea
                id="experiment-config-description"
                onChange={(event) => setDescription(event.target.value)}
                value={description}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="experiment-conversion-event">Conversion event name</FieldLabel>
              <Input
                id="experiment-conversion-event"
                onChange={(event) => setConversionEventName(event.target.value)}
                placeholder="purchase_completed"
                required
                type="text"
                value={conversionEventName}
              />
            </Field>

            <div className="flex flex-col gap-4">
              <FieldLabel>Allocation</FieldLabel>
              {variants.length === 2 ? (
                <BinaryRolloutSlider
                  allocations={allocations}
                  canEdit
                  onChange={setAllocations}
                  variants={variants}
                />
              ) : (
                variants.map((variant) => {
                  const percentage = allocations.find((a) => a.variantId === variant.id)?.percentage ?? 0
                  return (
                    <div className="flex flex-col gap-1.5" key={variant.id}>
                      <div className="flex items-center justify-between text-sm">
                        <span className="font-medium">{variant.name}</span>
                        <span className="tabular-nums text-muted-foreground">
                          {(percentage / 100).toFixed(0)}%
                        </span>
                      </div>
                      <Slider
                        aria-label={`${variant.name} percentage`}
                        maxValue={10000}
                        minValue={0}
                        onChange={(value) => updatePercentage(variant.id, value as number)}
                        step={100}
                        value={percentage}
                      >
                        <SliderTrack />
                      </Slider>
                    </div>
                  )
                })
              )}
              <p className={`text-sm ${total === 10000 ? "text-muted-foreground" : "text-destructive"}`}>
                Total: {(total / 100).toFixed(0)}% ({total} / 10000)
              </p>
            </div>

            {errorMessage && <p className="text-sm text-destructive">{errorMessage}</p>}
          </FieldGroup>
          <Button
            className="w-fit"
            isDisabled={updateExperiment.isPending || total !== 10000}
            type="submit"
          >
            {updateExperiment.isPending ? "Saving..." : "Save configuration"}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

function SummaryCard({ experiment, variants }: { experiment: Experiment; variants: FeatureFlagVariant[] }) {
  function variantName(variantId: string) {
    return variants.find((v) => v.id === variantId)?.name ?? variantId
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Configuration</CardTitle>
        <CardDescription>Frozen for the life of this experiment.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3 text-sm">
        <div>
          <span className="text-muted-foreground">Conversion event: </span>
          <span className="font-medium">{experiment.conversionEventName}</span>
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-muted-foreground">Allocation:</span>
          {experiment.allocation?.allocations.map((allocation) => (
            <div className="flex items-center gap-2" key={allocation.variantId}>
              <span className="w-32 truncate font-medium">{variantName(allocation.variantId)}</span>
              <span className="text-muted-foreground">{allocation.percentage / 100}%</span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

function MetricsCard({
  projectKey,
  experimentKey,
  variants,
}: {
  projectKey: string
  experimentKey: string
  variants: FeatureFlagVariant[]
}) {
  const metrics = useExperimentMetrics(projectKey, experimentKey)

  function variantName(variantId: string) {
    return variants.find((v) => v.id === variantId)?.name ?? variantId
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Metrics</CardTitle>
        <CardDescription>Per-variant assignment and conversion counts.</CardDescription>
      </CardHeader>
      <CardContent>
        {metrics.isPending ? (
          <Skeleton className="h-24 w-full" />
        ) : metrics.data && metrics.data.length > 0 ? (
          <Table aria-label="Experiment metrics">
            <TableHeader>
              <TableHead isRowHeader>Variant</TableHead>
              <TableHead>Assigned</TableHead>
              <TableHead>Converted</TableHead>
              <TableHead>Conversion rate</TableHead>
            </TableHeader>
            <TableBody items={metrics.data}>
              {(metric) => (
                <TableRow id={metric.variantId}>
                  <TableCell>{variantName(metric.variantId)}</TableCell>
                  <TableCell>{metric.assignedCount}</TableCell>
                  <TableCell>{metric.conversionCount}</TableCell>
                  <TableCell>{(metric.conversionRate * 100).toFixed(1)}%</TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        ) : (
          <p className="py-8 text-center text-sm text-muted-foreground">No metrics yet.</p>
        )}
      </CardContent>
    </Card>
  )
}


function BinaryRolloutSlider({
  variants,
  allocations,
  canEdit,
  onChange,
}: {
  variants: FeatureFlagVariant[]
  allocations: Allocation[]
  canEdit: boolean
  onChange: (allocations: Allocation[]) => void
}) {
  const [left, right] = variants
  const rightPercentage = allocations.find((a) => a.variantId === right.id)?.percentage ?? 0

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between text-sm font-medium">
        <span>{left.name}</span>
        <span>{right.name}</span>
      </div>
      <Slider
        aria-label={`${right.name} percentage`}
        isDisabled={!canEdit}
        maxValue={10000}
        minValue={0}
        onChange={(value) => {
          const rightValue = value as number
          onChange([
            { variantId: left.id, percentage: 10000 - rightValue },
            { variantId: right.id, percentage: rightValue },
          ])
        }}
        step={100}
        value={rightPercentage}
      >
        <SliderTrack />
      </Slider>
      <div className="flex items-center justify-between text-xs tabular-nums text-muted-foreground">
        <span>{((10000 - rightPercentage) / 100).toFixed(0)}%</span>
        <span>{(rightPercentage / 100).toFixed(0)}%</span>
      </div>
    </div>
  )
}
