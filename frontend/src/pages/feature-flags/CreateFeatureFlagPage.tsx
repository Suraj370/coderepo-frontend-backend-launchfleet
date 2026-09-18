import { useEffect, useState } from "react"
import { ArrowRight, Check, Layers, Plus, ToggleRight, Trash2 } from "lucide-react"
import { cn } from "cn"
import { Link, useNavigate } from "@tanstack/react-router"

import { useEnvironmentContext } from "@/app/environment-context"
import { getApiErrorMessage } from "@/api/errors"
import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { useCreateFeatureFlag } from "@/features/featureflags/hooks/useCreateFeatureFlag"
import type { CreateFeatureFlagVariant, FeatureFlagType } from "@/features/featureflags/types"

function slugify(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "")
}

const wizardSteps = [
  { number: 1, label: "Basic Info" },
  { number: 2, label: "Variants" },
  { number: 3, label: "Targeting" },
  { number: 4, label: "Review" },
] as const

function WizardStepIndicator({ current }: { current: number }) {
  return (
    <div className="flex items-center justify-center pb-2">
      {wizardSteps.map((step, index) => (
        <div className="flex items-center" key={step.number}>
          <div className="flex flex-col items-center gap-1.5">
            <div
              className={cn(
                "flex size-8 shrink-0 items-center justify-center rounded-full border text-sm font-semibold transition-colors",
                current > step.number
                  ? "border-primary bg-primary text-primary-foreground"
                  : current === step.number
                    ? "border-primary text-primary"
                    : "border-border text-muted-foreground",
              )}
            >
              {current > step.number ? <Check size={16} /> : step.number}
            </div>
            <span
              className={cn(
                "text-xs whitespace-nowrap",
                current >= step.number ? "font-medium text-foreground" : "text-muted-foreground",
              )}
            >
              {step.label}
            </span>
          </div>
          {index < wizardSteps.length - 1 && (
            <div
              className={cn(
                "mx-3 mb-5 h-px w-12 shrink-0 transition-colors sm:w-20",
                current > step.number ? "bg-primary" : "bg-border",
              )}
            />
          )}
        </div>
      ))}
    </div>
  )
}

export function CreateFeatureFlagPage() {
  const navigate = useNavigate()
  const { projectKey } = useEnvironmentContext()
  const createFlag = useCreateFeatureFlag(projectKey ?? "")
  const [step, setStep] = useState(1)
  const [name, setName] = useState("")
  const [key, setKey] = useState("")
  const [keyEditedManually, setKeyEditedManually] = useState(false)
  const [description, setDescription] = useState("")
  const [type, setType] = useState<FeatureFlagType>("BOOLEAN")
  const [variants, setVariants] = useState<CreateFeatureFlagVariant[]>([
    { key: "", name: "", value: "" },
    { key: "", name: "", value: "" },
  ])
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!createFlag.error) {
      setErrorMessage(null)
      return
    }

    let cancelled = false

    getApiErrorMessage(createFlag.error).then((message) => {
      if (!cancelled) {
        setErrorMessage(message)
      }
    })

    return () => {
      cancelled = true
    }
  }, [createFlag.error])

  function handleNameChange(value: string) {
    setName(value)
    if (!keyEditedManually) {
      setKey(slugify(value))
    }
  }

  function updateVariant(index: number, patch: Partial<CreateFeatureFlagVariant>) {
    setVariants((current) =>
      current.map((variant, i) => (i === index ? { ...variant, ...patch } : variant)),
    )
  }

  function addVariant() {
    setVariants((current) => [...current, { key: "", name: "", value: "" }])
  }

  function removeVariant(index: number) {
    setVariants((current) => current.filter((_, i) => i !== index))
  }

  const step1Valid = name.trim().length > 0 && key.trim().length > 0
  const step2Valid =
    type === "BOOLEAN" || variants.every((variant) => variant.key.trim() && variant.name.trim())
  const canAdvance = step === 1 ? step1Valid : step === 2 ? step2Valid : true

  function handleCreate() {
    createFlag.mutate(
      {
        key,
        name,
        description: description || undefined,
        type,
        variants: type === "MULTIVARIANT" ? variants : null,
      },
      {
        onSuccess: (flag) => {
          navigate({ to: "/feature-flags/$flagKey", params: { flagKey: flag.key } })
        },
      },
    )
  }

  if (!projectKey) {
    return null
  }

  return (
    <div className="flex flex-col gap-6">
      <Link className="w-fit text-sm text-muted-foreground no-underline hover:underline" to="/feature-flags">
        ← Feature flags
      </Link>

      <div>
        <h1 className="text-2xl font-semibold">Create a feature flag</h1>
        <p className="text-sm text-muted-foreground">
          Set up a new feature flag in a few steps.
        </p>
      </div>

      <div className="flex flex-col gap-6">
        <WizardStepIndicator current={step} />
        <form onSubmit={(event) => event.preventDefault()}>
            {step === 1 && (
              <FieldGroup>
                <Field data-invalid={errorMessage ? true : undefined}>
                  <FieldLabel htmlFor="flag-name">Name</FieldLabel>
                  <Input
                    id="flag-name"
                    onChange={(event) => handleNameChange(event.target.value)}
                    placeholder="New checkout flow"
                    required
                    autoFocus
                    type="text"
                    value={name}
                  />
                </Field>
                <Field data-invalid={errorMessage ? true : undefined}>
                  <FieldLabel htmlFor="flag-key">Key</FieldLabel>
                  <Input
                    id="flag-key"
                    onChange={(event) => {
                      setKeyEditedManually(true)
                      setKey(event.target.value)
                    }}
                    placeholder="new-checkout-flow"
                    required
                    type="text"
                    value={key}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="flag-description">Description</FieldLabel>
                  <Textarea
                    id="flag-description"
                    onChange={(event) => setDescription(event.target.value)}
                    placeholder="What does this flag control?"
                    value={description}
                  />
                </Field>
                <Field>
                  <FieldLabel>Flag Type</FieldLabel>
                  <div className="grid grid-cols-2 gap-3">
                    <button
                      className={cn(
                        "flex flex-col items-start gap-1.5 rounded-lg border p-4 text-left transition-colors",
                        type === "BOOLEAN"
                          ? "border-primary bg-accent"
                          : "border-border hover:bg-muted",
                      )}
                      onClick={() => setType("BOOLEAN")}
                      type="button"
                    >
                      <ToggleRight className="text-primary" size={20} />
                      <span className="text-sm font-medium">Boolean</span>
                      <span className="text-xs text-muted-foreground">Simple on/off flag</span>
                    </button>
                    <button
                      className={cn(
                        "flex flex-col items-start gap-1.5 rounded-lg border p-4 text-left transition-colors",
                        type === "MULTIVARIANT"
                          ? "border-primary bg-accent"
                          : "border-border hover:bg-muted",
                      )}
                      onClick={() => setType("MULTIVARIANT")}
                      type="button"
                    >
                      <Layers className="text-primary" size={20} />
                      <span className="text-sm font-medium">Multivariant</span>
                      <span className="text-xs text-muted-foreground">
                        Multiple named variants
                      </span>
                    </button>
                  </div>
                </Field>
                {errorMessage && <FieldError>{errorMessage}</FieldError>}
              </FieldGroup>
            )}

            {step === 2 &&
              (type === "MULTIVARIANT" ? (
                <div className="flex flex-col gap-2">
                  <FieldLabel>Variants</FieldLabel>
                  {variants.map((variant, index) => (
                    <div className="flex items-center gap-2" key={index}>
                      <Input
                        aria-label="Variant key"
                        onChange={(event) => updateVariant(index, { key: event.target.value })}
                        placeholder="key"
                        required
                        value={variant.key}
                      />
                      <Input
                        aria-label="Variant name"
                        onChange={(event) => updateVariant(index, { name: event.target.value })}
                        placeholder="Display name"
                        required
                        value={variant.name}
                      />
                      <Input
                        aria-label="Variant value"
                        onChange={(event) => updateVariant(index, { value: event.target.value })}
                        placeholder="value"
                        value={String(variant.value ?? "")}
                      />
                      <Button
                        aria-label="Remove variant"
                        isDisabled={variants.length <= 2}
                        onPress={() => removeVariant(index)}
                        size="icon-sm"
                        type="button"
                        variant="ghost"
                      >
                        <Trash2 />
                      </Button>
                    </div>
                  ))}
                  <Button
                    className="w-fit"
                    onPress={addVariant}
                    size="sm"
                    type="button"
                    variant="outline"
                  >
                    <Plus /> Add variant
                  </Button>
                </div>
              ) : (
                <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
                  Boolean flags always get the fixed <span className="font-medium">True</span> /{" "}
                  <span className="font-medium">False</span> variant pair automatically - nothing
                  to configure here.
                </p>
              ))}

            {step === 3 && (
              <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
                Targeting rules are configured per environment, once this flag exists - head to
                the flag&apos;s detail page after creating it to add rules for each environment.
              </p>
            )}

            {step === 4 && (
              <div className="flex flex-col gap-3 text-sm">
                <ReviewRow label="Name" value={name} />
                <ReviewRow label="Key" value={key} />
                {description && <ReviewRow label="Description" value={description} />}
                <ReviewRow label="Type" value={type === "BOOLEAN" ? "Boolean" : "Multivariant"} />
                {type === "MULTIVARIANT" && (
                  <ReviewRow
                    label="Variants"
                    value={variants.map((variant) => variant.name || variant.key).join(", ")}
                  />
                )}
                {errorMessage && <FieldError>{errorMessage}</FieldError>}
              </div>
            )}
          </form>

          <div className="sticky bottom-0 mt-6 flex justify-end gap-2 p-4">
            {step > 1 ? (
              <Button onPress={() => setStep((current) => current - 1)} type="button" variant="outline">
                Back
              </Button>
            ) : (
              <Button onPress={() => navigate({ to: "/feature-flags" })} type="button" variant="outline">
                Cancel
              </Button>
            )}
            {step < 4 ? (
              <Button
                isDisabled={!canAdvance}
                onPress={() => canAdvance && setStep((current) => current + 1)}
                type="button"
              >
                Next <ArrowRight />
              </Button>
            ) : (
              <Button isDisabled={createFlag.isPending} onPress={handleCreate} type="button">
                {createFlag.isPending ? "Creating..." : "Create flag"}
              </Button>
            )}
          </div>
      </div>
    </div>
  )
}

function ReviewRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-4 border-b pb-2 last:border-0 last:pb-0">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right font-medium">{value}</span>
    </div>
  )
}
