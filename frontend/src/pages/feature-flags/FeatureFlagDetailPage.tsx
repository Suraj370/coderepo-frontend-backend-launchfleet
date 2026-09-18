import { useState } from "react"
import { Plus, Trash2 } from "lucide-react"
import { Link, useParams } from "@tanstack/react-router"

import { useEnvironmentContext } from "@/app/environment-context"
import { getApiErrorMessage } from "@/api/errors"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldLabel } from "@/components/ui/field"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Slider, SliderTrack } from "@/components/ui/slider"
import { Switch } from "@/components/ui/switch"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useSubmitApprovalRequest } from "@/features/approvals/hooks/useApprovalMutations"
import { useFeatureFlag } from "@/features/featureflags/hooks/useFeatureFlag"
import { useSegments } from "@/features/segments/hooks/useSegments"
import {
  useRemoveRollout,
  useRemoveTargetingRule,
  useRetireFeatureFlag,
  useSetRollout,
  useUpdateFlagEnvironmentConfig,
} from "@/features/featureflags/hooks/useFeatureFlagMutations"
import type {
  Allocation,
  FeatureFlag,
  FeatureFlagConfig,
  FeatureFlagVariant,
  TargetingRule,
} from "@/features/featureflags/types"

export function FeatureFlagDetailPage() {
  const { flagKey } = useParams({ from: "/_app/feature-flags_/$flagKey" })
  const { projectKey, projects } = useEnvironmentContext()
  const flag = useFeatureFlag(projectKey, flagKey)
  const canEdit = projects.find((project) => project.key === projectKey)?.role !== "VIEWER"

  const [manualEnvKey, setManualEnvKey] = useState<string | null>(null)
  const envKey =
    manualEnvKey && flag.data?.environments.some((env) => env.environmentKey === manualEnvKey)
      ? manualEnvKey
      : (flag.data?.environments[0]?.environmentKey ?? null)

  if (flag.isPending) {
    return (
      <div className="flex flex-col gap-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-48 w-full" />
      </div>
    )
  }

  if (flag.isError || !flag.data || !projectKey) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Flag not found</CardTitle>
          <CardDescription>Something went wrong loading this feature flag.</CardDescription>
        </CardHeader>
        <CardContent>
          <Link to="/feature-flags">Back to feature flags</Link>
        </CardContent>
      </Card>
    )
  }

  const config = flag.data.environments.find((env) => env.environmentKey === envKey) ?? null

  return (
    <div className="flex flex-col gap-6">
      <FlagHeader canEdit={canEdit} flag={flag.data} projectKey={projectKey} />

      {flag.data.environments.length === 0 ? (
        <Card>
          <CardContent className="py-8 text-center text-sm text-muted-foreground">
            This project has no environments yet.
          </CardContent>
        </Card>
      ) : (
        <Tabs onSelectionChange={(key) => setManualEnvKey(String(key))} selectedKey={envKey ?? undefined}>
          <TabsList>
            {flag.data.environments.map((env) => (
              <TabsTrigger id={env.environmentKey} key={env.environmentKey}>
                {env.environmentKey}
              </TabsTrigger>
            ))}
          </TabsList>
          {flag.data.environments.map((env) => (
            <TabsContent id={env.environmentKey} key={env.environmentKey}>
              {config && config.environmentKey === env.environmentKey && (
                <EnvironmentConfigPanel
                  canEdit={canEdit}
                  config={config}
                  flagKey={flag.data.key}
                  projectKey={projectKey}
                  variants={flag.data.variants}
                />
              )}
            </TabsContent>
          ))}
        </Tabs>
      )}
    </div>
  )
}

function FlagHeader({
  flag,
  projectKey,
  canEdit,
}: {
  flag: FeatureFlag
  projectKey: string
  canEdit: boolean
}) {
  const retireFlag = useRetireFeatureFlag(projectKey, flag.key)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  function handleRetire() {
    retireFlag.mutate(undefined, {
      onError: async (error) => {
        setErrorMessage(await getApiErrorMessage(error))
      },
    })
  }

  return (
    <div className="flex flex-col gap-2">
      <Link className="w-fit text-sm text-muted-foreground no-underline hover:underline" to="/feature-flags">
        ← Feature flags
      </Link>
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-semibold">{flag.name}</h1>
            <Badge variant={flag.status === "ACTIVE" ? "default" : "secondary"}>{flag.status}</Badge>
            <Badge variant="outline">{flag.type}</Badge>
          </div>
          <p className="text-sm text-muted-foreground">{flag.key}</p>
          {flag.description && <p className="mt-1 text-sm">{flag.description}</p>}
        </div>
        {canEdit && flag.status === "ACTIVE" && (
          <Button
            isDisabled={retireFlag.isPending}
            onPress={handleRetire}
            variant="destructive"
          >
            {retireFlag.isPending ? "Retiring..." : "Retire flag"}
          </Button>
        )}
      </div>
      {errorMessage && <p className="text-sm text-destructive">{errorMessage}</p>}
    </div>
  )
}

function EnvironmentConfigPanel({
  projectKey,
  flagKey,
  config,
  variants,
  canEdit,
}: {
  projectKey: string
  flagKey: string
  config: FeatureFlagConfig
  variants: FeatureFlagVariant[]
  canEdit: boolean
}) {
  const updateConfig = useUpdateFlagEnvironmentConfig(projectKey, flagKey, config.environmentKey)

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>Environment configuration</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex items-center gap-3">
            <Switch
              isDisabled={!canEdit || updateConfig.isPending}
              isSelected={config.enabled}
              onChange={(enabled) => updateConfig.mutate({ enabled })}
            />
            <span className="text-sm font-medium">{config.enabled ? "Enabled" : "Disabled"}</span>
          </div>
          <Field className="max-w-xs">
            <FieldLabel htmlFor="default-variant">Default variant</FieldLabel>
            <Select
              aria-label="Default variant"
              id="default-variant"
              isDisabled={!canEdit || updateConfig.isPending}
              onSelectionChange={(id) => updateConfig.mutate({ defaultVariantId: String(id) })}
              selectedKey={config.defaultVariantId}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {variants.map((variant) => (
                  <SelectItem id={variant.id} key={variant.id}>
                    {variant.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
        </CardContent>
      </Card>

      <TargetingRulesCard
        canEdit={canEdit}
        config={config}
        flagKey={flagKey}
        projectKey={projectKey}
        variants={variants}
      />

      {canEdit && (
        <SubmitApprovalCard config={config} flagKey={flagKey} projectKey={projectKey} variants={variants} />
      )}

      <RolloutCard
        canEdit={canEdit}
        config={config}
        flagKey={flagKey}
        projectKey={projectKey}
        variants={variants}
      />
    </div>
  )
}

function describeConditions(
  conditions: TargetingRule["conditions"],
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

function TargetingRulesCard({
  projectKey,
  flagKey,
  config,
  variants,
  canEdit,
}: {
  projectKey: string
  flagKey: string
  config: FeatureFlagConfig
  variants: FeatureFlagVariant[]
  canEdit: boolean
}) {
  const removeRule = useRemoveTargetingRule(projectKey, flagKey, config.environmentKey)
  const segments = useSegments(projectKey)
  const segmentNameById = new Map(segments.data?.map((segment) => [segment.id, segment.name]))
  const sortedRules = [...config.targetingRules].sort((a, b) => a.priority - b.priority)

  function variantName(variantId: string) {
    return variants.find((v) => v.id === variantId)?.name ?? variantId
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <div>
          <CardTitle>Targeting rules</CardTitle>
          <CardDescription>Evaluated in priority order, before any rollout.</CardDescription>
        </div>
        {canEdit && (
          <Link
            className={buttonVariants({ size: "sm" })}
            params={{ flagKey, environmentKey: config.environmentKey }}
            to="/feature-flags/$flagKey/environments/$environmentKey/targeting-rules/new"
          >
            <Plus /> Add rule
          </Link>
        )}
      </CardHeader>
      <CardContent>
        {sortedRules.length === 0 ? (
          <p className="py-4 text-center text-sm text-muted-foreground">No targeting rules yet.</p>
        ) : (
          <Table aria-label="Targeting rules">
            <TableHeader>
              <TableHead isRowHeader>Priority</TableHead>
              <TableHead>Conditions</TableHead>
              <TableHead>Serves</TableHead>
              <TableHead>{""}</TableHead>
            </TableHeader>
            <TableBody items={sortedRules}>
              {(rule) => (
                <TableRow id={rule.id}>
                  <TableCell>{rule.priority}</TableCell>
                  <TableCell className="max-w-md text-muted-foreground">
                    {describeConditions(rule.conditions, segmentNameById)}
                  </TableCell>
                  <TableCell>{variantName(rule.variantId)}</TableCell>
                  <TableCell>
                    {canEdit && (
                      <div className="flex gap-1">
                        <Link
                          className={buttonVariants({ size: "sm", variant: "outline" })}
                          params={{
                            flagKey,
                            environmentKey: config.environmentKey,
                            ruleId: rule.id,
                          }}
                          to="/feature-flags/$flagKey/environments/$environmentKey/targeting-rules/$ruleId/edit"
                        >
                          Edit
                        </Link>
                        <Button
                          aria-label="Remove rule"
                          isDisabled={removeRule.isPending}
                          onPress={() => removeRule.mutate(rule.id)}
                          size="icon-sm"
                          variant="ghost"
                        >
                          <Trash2 />
                        </Button>
                      </div>
                    )}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

function SubmitApprovalCard({
  projectKey,
  flagKey,
  config,
  variants,
}: {
  projectKey: string
  flagKey: string
  config: FeatureFlagConfig
  variants: FeatureFlagVariant[]
}) {
  const submitApproval = useSubmitApprovalRequest(projectKey, flagKey, config.environmentKey)
  const [enabled, setEnabled] = useState(config.enabled)
  const [defaultVariantId, setDefaultVariantId] = useState(config.defaultVariantId)
  const [allocations, setAllocations] = useState<Allocation[]>(
    config.rollout?.allocations ?? variants.map((v) => ({ variantId: v.id, percentage: 0 })),
  )
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [submittedId, setSubmittedId] = useState<string | null>(null)

  const total = allocations.reduce((sum, allocation) => sum + allocation.percentage, 0)

  function updatePercentage(variantId: string, percentage: number) {
    setAllocations((current) =>
      current.map((allocation) =>
        allocation.variantId === variantId ? { ...allocation, percentage } : allocation,
      ),
    )
  }

  async function handleSubmit() {
    setErrorMessage(null)
    try {
      const request = await submitApproval.mutateAsync({
        enabled,
        defaultVariantId,
        targetingRules: config.targetingRules.map((rule) => ({
          priority: rule.priority,
          conditions: rule.conditions.map((condition) => ({
            type: condition.type,
            attribute: condition.attribute ?? undefined,
            operator: condition.operator,
            values: condition.values,
          })),
          variantId: rule.variantId,
        })),
        rollout: total === 10000 ? { allocations } : null,
      })
      setSubmittedId(request.id)
    } catch (error) {
      setErrorMessage(await getApiErrorMessage(error))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Submit for approval</CardTitle>
        <CardDescription>
          Propose enabled state, default variant, and rollout for a second person to review. Targeting
          rules carry through unchanged.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex items-center gap-3">
          <Switch isSelected={enabled} onChange={setEnabled} />
          <span className="text-sm font-medium">{enabled ? "Enabled" : "Disabled"}</span>
        </div>
        <Field className="max-w-xs">
          <FieldLabel htmlFor="propose-default-variant">Default variant</FieldLabel>
          <Select
            aria-label="Proposed default variant"
            id="propose-default-variant"
            onSelectionChange={(id) => setDefaultVariantId(String(id))}
            selectedKey={defaultVariantId}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {variants.map((variant) => (
                <SelectItem id={variant.id} key={variant.id}>
                  {variant.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>

        <div className="flex flex-col gap-4">
          <FieldLabel>Rollout</FieldLabel>
          {variants.length === 2 ? (
            <BinaryRolloutSlider allocations={allocations} canEdit onChange={setAllocations} variants={variants} />
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
        {submittedId && (
          <p className="text-sm text-muted-foreground">
            Submitted.{" "}
            <Link className="text-foreground underline" params={{ requestId: submittedId }} to="/approvals/$requestId">
              View approval request
            </Link>
          </p>
        )}
        <Button
          className="w-fit"
          isDisabled={submitApproval.isPending || total !== 10000}
          onPress={handleSubmit}
        >
          {submitApproval.isPending ? "Submitting..." : "Submit for approval"}
        </Button>
      </CardContent>
    </Card>
  )
}

function RolloutCard({
  projectKey,
  flagKey,
  config,
  variants,
  canEdit,
}: {
  projectKey: string
  flagKey: string
  config: FeatureFlagConfig
  variants: FeatureFlagVariant[]
  canEdit: boolean
}) {
  const setRollout = useSetRollout(projectKey, flagKey, config.environmentKey)
  const removeRollout = useRemoveRollout(projectKey, flagKey, config.environmentKey)
  const [allocations, setAllocations] = useState<Allocation[]>(
    config.rollout?.allocations ?? variants.map((v) => ({ variantId: v.id, percentage: 0 })),
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

  async function handleSave() {
    setErrorMessage(null)
    try {
      await setRollout.mutateAsync({ allocations })
    } catch (error) {
      setErrorMessage(await getApiErrorMessage(error))
    }
  }

  async function handleRemove() {
    setErrorMessage(null)
    try {
      await removeRollout.mutateAsync()
      setAllocations(variants.map((v) => ({ variantId: v.id, percentage: 0 })))
    } catch (error) {
      setErrorMessage(await getApiErrorMessage(error))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Progressive rollout</CardTitle>
        <CardDescription>
          Fallback split used when no targeting rule matches. Basis points, must sum to 10000
          (100%).
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        {variants.length === 2 ? (
          <BinaryRolloutSlider
            allocations={allocations}
            canEdit={canEdit}
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
                  isDisabled={!canEdit}
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
        {errorMessage && <p className="text-sm text-destructive">{errorMessage}</p>}
        {canEdit && (
          <div className="flex gap-2">
            <Button isDisabled={setRollout.isPending || total !== 10000} onPress={handleSave} size="sm">
              {setRollout.isPending ? "Saving..." : "Save rollout"}
            </Button>
            {config.rollout && (
              <Button
                isDisabled={removeRollout.isPending}
                onPress={handleRemove}
                size="sm"
                variant="outline"
              >
                Remove rollout
              </Button>
            )}
          </div>
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
