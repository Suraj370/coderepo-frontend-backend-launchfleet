import { type FormEvent, useState } from "react"
import { Plus, Trash2 } from "lucide-react"
import { Link, useNavigate, useParams } from "@tanstack/react-router"

import { useEnvironmentContext } from "@/app/environment-context"
import { getApiErrorMessage } from "@/api/errors"
import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { useCreateSegment, useUpdateSegment } from "@/features/segments/hooks/useSegmentMutations"
import { useSegments } from "@/features/segments/hooks/useSegments"
import type { ConditionOperator, Segment, SegmentConditionRequest, SegmentConditionType } from "@/features/segments/types"

function slugify(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "")
}

interface ConditionForm {
  type: SegmentConditionType
  attribute: string
  operator: ConditionOperator
  valuesText: string
}

function conditionsToForm(segment: Segment | null): ConditionForm[] {
  if (!segment || segment.conditions.length === 0) {
    return [{ type: "ATTRIBUTE", attribute: "", operator: "EQUALS", valuesText: "" }]
  }

  return segment.conditions.map((condition) => ({
    type: condition.type,
    attribute: condition.attribute ?? "",
    operator: condition.operator,
    valuesText: condition.values.join(", "),
  }))
}

export function CreateSegmentPage() {
  return <SegmentForm segment={null} />
}

export function EditSegmentPage() {
  const { segmentKey } = useParams({ from: "/_app/segments_/$segmentKey/edit" })
  const { projectKey } = useEnvironmentContext()
  const segments = useSegments(projectKey)
  const segment = segments.data?.find((s) => s.key === segmentKey) ?? null

  if (segments.isPending) {
    return null
  }

  return <SegmentForm segment={segment} />
}

function SegmentForm({ segment }: { segment: Segment | null }) {
  const navigate = useNavigate()
  const { projectKey } = useEnvironmentContext()
  const createSegment = useCreateSegment(projectKey ?? "")
  const updateSegment = useUpdateSegment(projectKey ?? "")
  const isPending = createSegment.isPending || updateSegment.isPending

  const [name, setName] = useState(segment?.name ?? "")
  const [key, setKey] = useState(segment?.key ?? "")
  const [keyEditedManually, setKeyEditedManually] = useState(Boolean(segment))
  const [conditions, setConditions] = useState<ConditionForm[]>(conditionsToForm(segment))
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  function handleNameChange(value: string) {
    setName(value)
    if (!keyEditedManually) {
      setKey(slugify(value))
    }
  }

  function updateCondition(index: number, patch: Partial<ConditionForm>) {
    setConditions((current) =>
      current.map((condition, i) => (i === index ? { ...condition, ...patch } : condition)),
    )
  }

  function addCondition() {
    setConditions((current) => [
      ...current,
      { type: "ATTRIBUTE", attribute: "", operator: "EQUALS", valuesText: "" },
    ])
  }

  function removeCondition(index: number) {
    setConditions((current) => current.filter((_, i) => i !== index))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrorMessage(null)

    const requestConditions: SegmentConditionRequest[] = conditions.map((condition) => ({
      type: condition.type,
      attribute: condition.type === "ATTRIBUTE" ? condition.attribute : undefined,
      operator: condition.operator,
      values: condition.valuesText
        .split(",")
        .map((value) => value.trim())
        .filter(Boolean),
    }))

    try {
      if (segment) {
        await updateSegment.mutateAsync({
          segmentKey: segment.key,
          request: { name, conditions: requestConditions },
        })
      } else {
        await createSegment.mutateAsync({ key, name, conditions: requestConditions })
      }
      navigate({ to: "/segments" })
    } catch (error) {
      setErrorMessage(await getApiErrorMessage(error))
    }
  }

  if (!projectKey) {
    return null
  }

  return (
    <div className="flex flex-col gap-6">
      <Link className="w-fit text-sm text-muted-foreground no-underline hover:underline" to="/segments">
        ← Segments
      </Link>

      <div>
        <h1 className="text-2xl font-semibold">{segment ? "Edit segment" : "Create a segment"}</h1>
        <p className="text-sm text-muted-foreground">
          Reusable, project-scoped user groups for targeting-rule SEGMENT_MATCH conditions.
        </p>
      </div>

      <form onSubmit={handleSubmit}>
        <FieldGroup>
          <Field data-invalid={errorMessage ? true : undefined}>
            <FieldLabel htmlFor="segment-name">Name</FieldLabel>
            <Input
              autoFocus
              id="segment-name"
              onChange={(event) => handleNameChange(event.target.value)}
              placeholder="Enterprise customers"
              required
              type="text"
              value={name}
            />
          </Field>
          {!segment && (
            <Field data-invalid={errorMessage ? true : undefined}>
              <FieldLabel htmlFor="segment-key">Key</FieldLabel>
              <Input
                id="segment-key"
                onChange={(event) => {
                  setKeyEditedManually(true)
                  setKey(event.target.value)
                }}
                placeholder="enterprise-customers"
                required
                type="text"
                value={key}
              />
            </Field>
          )}

          <div className="flex flex-col gap-3">
            <FieldLabel>Conditions (all must match)</FieldLabel>
            {conditions.map((condition, index) => (
              <div className="flex flex-col gap-2 rounded-lg border p-2" key={index}>
                <div className="flex gap-2">
                  <Select
                    aria-label="Condition type"
                    onSelectionChange={(value) =>
                      updateCondition(index, { type: String(value) as SegmentConditionType })
                    }
                    selectedKey={condition.type}
                  >
                    <SelectTrigger className="w-40">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem id="ATTRIBUTE">Attribute</SelectItem>
                      <SelectItem id="USER_KEY">User key</SelectItem>
                    </SelectContent>
                  </Select>
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
                <Input
                  aria-label="Values"
                  onChange={(event) => updateCondition(index, { valuesText: event.target.value })}
                  placeholder="comma-separated values"
                  required
                  value={condition.valuesText}
                />
              </div>
            ))}
            <Button className="w-fit" onPress={addCondition} size="sm" type="button" variant="outline">
              <Plus /> Add condition
            </Button>
          </div>

          {errorMessage && <FieldError>{errorMessage}</FieldError>}
        </FieldGroup>

        <div className="sticky bottom-0 mt-6 flex justify-end gap-2 p-4">
          <Button onPress={() => navigate({ to: "/segments" })} type="button" variant="outline">
            Cancel
          </Button>
          <Button isDisabled={isPending} type="submit">
            {isPending ? "Saving..." : "Save segment"}
          </Button>
        </div>
      </form>
    </div>
  )
}
