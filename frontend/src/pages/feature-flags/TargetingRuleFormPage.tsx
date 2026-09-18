import { type FormEvent, useState } from "react"
import { Plus, Trash2 } from "lucide-react"
import { Link, useNavigate, useParams } from "@tanstack/react-router"

import { useEnvironmentContext } from "@/app/environment-context"
import { getApiErrorMessage } from "@/api/errors"
import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { useFeatureFlag } from "@/features/featureflags/hooks/useFeatureFlag"
import { useAddTargetingRule, useUpdateTargetingRule } from "@/features/featureflags/hooks/useFeatureFlagMutations"
import { useSegments } from "@/features/segments/hooks/useSegments"
import type { ConditionOperator, ConditionType, TargetingRule, TargetingRuleRequest } from "@/features/featureflags/types"

interface ConditionForm {
  type: ConditionType
  attribute: string
  operator: ConditionOperator
  valuesText: string
  segmentId: string
}

function conditionsToForm(rule: TargetingRule | null): ConditionForm[] {
  if (!rule || rule.conditions.length === 0) {
    return [{ type: "ATTRIBUTE", attribute: "", operator: "EQUALS", valuesText: "", segmentId: "" }]
  }

  return rule.conditions.map((condition) => ({
    type: condition.type,
    attribute: condition.attribute ?? "",
    operator: condition.operator,
    valuesText: condition.type === "SEGMENT_MATCH" ? "" : condition.values.join(", "),
    segmentId: condition.type === "SEGMENT_MATCH" ? (condition.values[0] ?? "") : "",
  }))
}

export function CreateTargetingRulePage() {
  const { flagKey, environmentKey } = useParams({
    from: "/_app/feature-flags_/$flagKey_/environments/$environmentKey/targeting-rules/new",
  })

  return <TargetingRuleFormBody environmentKey={environmentKey} flagKey={flagKey} rule={null} />
}

export function EditTargetingRulePage() {
  const { flagKey, environmentKey, ruleId } = useParams({
    from: "/_app/feature-flags_/$flagKey_/environments/$environmentKey/targeting-rules/$ruleId/edit",
  })
  const { projectKey } = useEnvironmentContext()
  const flag = useFeatureFlag(projectKey, flagKey)
  const config = flag.data?.environments.find((env) => env.environmentKey === environmentKey)
  const rule = config?.targetingRules.find((r) => r.id === ruleId) ?? null

  if (flag.isPending) {
    return <Skeleton className="h-64 w-full" />
  }

  return <TargetingRuleFormBody environmentKey={environmentKey} flagKey={flagKey} rule={rule} />
}

function TargetingRuleFormBody({
  flagKey,
  environmentKey,
  rule,
}: {
  flagKey: string
  environmentKey: string
  rule: TargetingRule | null
}) {
  const navigate = useNavigate()
  const { projectKey } = useEnvironmentContext()
  const flag = useFeatureFlag(projectKey, flagKey)
  const addRule = useAddTargetingRule(projectKey ?? "", flagKey, environmentKey)
  const updateRule = useUpdateTargetingRule(projectKey ?? "", flagKey, environmentKey)
  const segments = useSegments(projectKey)
  const isPending = addRule.isPending || updateRule.isPending

  const variants = flag.data?.variants ?? []

  const [priority, setPriority] = useState(rule?.priority ?? 0)
  const [variantId, setVariantId] = useState(rule?.variantId ?? "")
  const [conditions, setConditions] = useState<ConditionForm[]>(conditionsToForm(rule))
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  function backToFlag() {
    navigate({ to: "/feature-flags/$flagKey", params: { flagKey } })
  }

  function updateCondition(index: number, patch: Partial<ConditionForm>) {
    setConditions((current) =>
      current.map((condition, i) => (i === index ? { ...condition, ...patch } : condition)),
    )
  }

  function addCondition() {
    setConditions((current) => [
      ...current,
      { type: "ATTRIBUTE", attribute: "", operator: "EQUALS", valuesText: "", segmentId: "" },
    ])
  }

  function removeCondition(index: number) {
    setConditions((current) => current.filter((_, i) => i !== index))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrorMessage(null)

    const request: TargetingRuleRequest = {
      priority,
      variantId,
      conditions: conditions.map((condition) => ({
        type: condition.type,
        attribute: condition.type === "ATTRIBUTE" ? condition.attribute : undefined,
        operator: condition.type === "SEGMENT_MATCH" ? "EQUALS" : condition.operator,
        values:
          condition.type === "SEGMENT_MATCH"
            ? condition.segmentId
              ? [condition.segmentId]
              : []
            : condition.valuesText
                .split(",")
                .map((value) => value.trim())
                .filter(Boolean),
      })),
    }

    const mutation = rule
      ? updateRule.mutateAsync({ ruleId: rule.id, request })
      : addRule.mutateAsync(request)

    try {
      await mutation
      backToFlag()
    } catch (error) {
      setErrorMessage(await getApiErrorMessage(error))
    }
  }

  if (flag.isPending) {
    return <Skeleton className="h-64 w-full" />
  }

  return (
    <div className="flex flex-col gap-6">
      <Link
        className="w-fit text-sm text-muted-foreground no-underline hover:underline"
        params={{ flagKey }}
        to="/feature-flags/$flagKey"
      >
        ← {flagKey}
      </Link>

      <div>
        <h1 className="text-2xl font-semibold">
          {rule ? "Edit targeting rule" : "Add targeting rule"}
        </h1>
        <p className="text-sm text-muted-foreground">
          Environment: <span className="font-medium text-foreground">{environmentKey}</span>
        </p>
      </div>

      <form onSubmit={handleSubmit}>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="rule-priority">Priority</FieldLabel>
            <Input
              id="rule-priority"
              min={0}
              onChange={(event) => setPriority(Number(event.target.value))}
              required
              type="number"
              value={priority}
            />
          </Field>

          <div className="flex flex-col gap-3">
            <FieldLabel>Conditions (all must match)</FieldLabel>
            {conditions.map((condition, index) => (
              <div className="flex flex-col gap-2 rounded-lg border p-2" key={index}>
                <div className="flex gap-2">
                  <Select
                    aria-label="Condition type"
                    onSelectionChange={(value) =>
                      updateCondition(index, {
                        type: String(value) as ConditionType,
                        ...(String(value) === "SEGMENT_MATCH" ? { segmentId: "" } : {}),
                      })
                    }
                    selectedKey={condition.type}
                  >
                    <SelectTrigger className="w-40">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem id="ATTRIBUTE">Attribute</SelectItem>
                      <SelectItem id="USER_KEY">User key</SelectItem>
                      <SelectItem id="SEGMENT_MATCH">Segment match</SelectItem>
                    </SelectContent>
                  </Select>
                  {condition.type !== "SEGMENT_MATCH" && (
                    <Select
                      aria-label="Operator"
                      onSelectionChange={(value) =>
                        updateCondition(index, { operator: String(value) as ConditionOperator })
                      }
                      selectedKey={condition.operator}
                    >
                      <SelectTrigger className="w-28">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem id="EQUALS">equals</SelectItem>
                        <SelectItem id="IN">in</SelectItem>
                      </SelectContent>
                    </Select>
                  )}
                  <Button
                    aria-label="Remove condition"
                    isDisabled={conditions.length <= 1}
                    onPress={() => removeCondition(index)}
                    size="icon-sm"
                    type="button"
                    variant="ghost"
                  >
                    <Trash2 />
                  </Button>
                </div>
                {condition.type === "ATTRIBUTE" && (
                  <Input
                    aria-label="Attribute name"
                    onChange={(event) => updateCondition(index, { attribute: event.target.value })}
                    placeholder="attribute name (e.g. plan)"
                    required
                    value={condition.attribute}
                  />
                )}
                {condition.type === "SEGMENT_MATCH" ? (
                  <Select
                    aria-label="Segment"
                    isDisabled={segments.isPending}
                    onSelectionChange={(value) => updateCondition(index, { segmentId: String(value) })}
                    placeholder="Select an active segment"
                    selectedKey={condition.segmentId || null}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {segments.data
                        ?.filter((segment) => segment.status === "ACTIVE")
                        .map((segment) => (
                          <SelectItem id={segment.id} key={segment.id}>
                            {segment.name} ({segment.key})
                          </SelectItem>
                        ))}
                    </SelectContent>
                  </Select>
                ) : (
                  <Input
                    aria-label="Values"
                    onChange={(event) => updateCondition(index, { valuesText: event.target.value })}
                    placeholder="comma-separated values"
                    required
                    value={condition.valuesText}
                  />
                )}
              </div>
            ))}
            <Button className="w-fit" onPress={addCondition} size="sm" type="button" variant="outline">
              <Plus /> Add condition
            </Button>
          </div>

          <Field>
            <FieldLabel htmlFor="rule-variant">Serve variant</FieldLabel>
            <Select
              aria-label="Serve variant"
              id="rule-variant"
              onSelectionChange={(value) => setVariantId(String(value))}
              selectedKey={variantId || null}
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

          {errorMessage && <FieldError>{errorMessage}</FieldError>}
        </FieldGroup>

        <div className="sticky bottom-0 mt-6 flex justify-end gap-2 p-4">
          <Button onPress={backToFlag} type="button" variant="outline">
            Cancel
          </Button>
          <Button isDisabled={isPending || !variantId} type="submit">
            {isPending ? "Saving..." : "Save rule"}
          </Button>
        </div>
      </form>
    </div>
  )
}
