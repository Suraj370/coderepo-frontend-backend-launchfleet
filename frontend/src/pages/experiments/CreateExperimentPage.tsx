import { type FormEvent, useEffect, useState } from "react"
import { Link, useNavigate } from "@tanstack/react-router"

import { useEnvironmentContext } from "@/app/environment-context"
import { getApiErrorMessage } from "@/api/errors"
import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { useEnvironments } from "@/features/environments/hooks/useEnvironments"
import { useCreateExperiment } from "@/features/experiments/hooks/useCreateExperiment"
import { useFeatureFlags } from "@/features/featureflags/hooks/useFeatureFlags"

function slugify(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "")
}

export function CreateExperimentPage() {
  const navigate = useNavigate()
  const { projectKey } = useEnvironmentContext()
  const flags = useFeatureFlags(projectKey)
  const environments = useEnvironments(projectKey)
  const createExperiment = useCreateExperiment(projectKey ?? "")

  const [name, setName] = useState("")
  const [key, setKey] = useState("")
  const [keyEditedManually, setKeyEditedManually] = useState(false)
  const [description, setDescription] = useState("")
  const [flagKey, setFlagKey] = useState("")
  const [environmentKey, setEnvironmentKey] = useState("")
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const activeFlags = (flags.data ?? []).filter((flag) => flag.status === "ACTIVE")

  useEffect(() => {
    if (!createExperiment.error) {
      setErrorMessage(null)
      return
    }

    let cancelled = false

    getApiErrorMessage(createExperiment.error).then((message) => {
      if (!cancelled) {
        setErrorMessage(message)
      }
    })

    return () => {
      cancelled = true
    }
  }, [createExperiment.error])

  function handleNameChange(value: string) {
    setName(value)
    if (!keyEditedManually) {
      setKey(slugify(value))
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    createExperiment.mutate(
      { key, name, description: description || undefined, flagKey, environmentKey },
      {
        onSuccess: (experiment) => {
          navigate({ to: "/experiments/$experimentKey", params: { experimentKey: experiment.key } })
        },
      },
    )
  }

  if (!projectKey) {
    return null
  }

  return (
    <div className="flex flex-col gap-6">
      <Link className="w-fit text-sm text-muted-foreground no-underline hover:underline" to="/experiments">
        ← Experiments
      </Link>

      <div>
        <h1 className="text-2xl font-semibold">Create an experiment</h1>
        <p className="text-sm text-muted-foreground">
          A/B experiments layered on a feature flag&apos;s variants, scoped to one environment.
        </p>
      </div>

      <form onSubmit={handleSubmit}>
        <FieldGroup>
          <Field data-invalid={errorMessage ? true : undefined}>
            <FieldLabel htmlFor="experiment-name">Name</FieldLabel>
            <Input
              autoFocus
              id="experiment-name"
              onChange={(event) => handleNameChange(event.target.value)}
              placeholder="Checkout button color"
              required
              type="text"
              value={name}
            />
          </Field>
          <Field data-invalid={errorMessage ? true : undefined}>
            <FieldLabel htmlFor="experiment-key">Key</FieldLabel>
            <Input
              id="experiment-key"
              onChange={(event) => {
                setKeyEditedManually(true)
                setKey(event.target.value)
              }}
              placeholder="checkout-button-color"
              required
              type="text"
              value={key}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="experiment-description">Description</FieldLabel>
            <Textarea
              id="experiment-description"
              onChange={(event) => setDescription(event.target.value)}
              placeholder="What is this experiment testing?"
              value={description}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="experiment-flag">Feature flag</FieldLabel>
            <Select
              aria-label="Feature flag"
              id="experiment-flag"
              onSelectionChange={(value) => setFlagKey(String(value))}
              placeholder="Select a flag"
              selectedKey={flagKey || null}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {activeFlags.map((flag) => (
                  <SelectItem id={flag.key} key={flag.key}>
                    {flag.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          <Field>
            <FieldLabel htmlFor="experiment-environment">Environment</FieldLabel>
            <Select
              aria-label="Environment"
              id="experiment-environment"
              onSelectionChange={(value) => setEnvironmentKey(String(value))}
              placeholder="Select an environment"
              selectedKey={environmentKey || null}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {environments.data?.map((env) => (
                  <SelectItem id={env.key} key={env.key}>
                    {env.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          {errorMessage && <FieldError>{errorMessage}</FieldError>}
        </FieldGroup>

        <div className="sticky bottom-0 mt-6 flex justify-end gap-2 p-4">
          <Button onPress={() => navigate({ to: "/experiments" })} type="button" variant="outline">
            Cancel
          </Button>
          <Button
            isDisabled={createExperiment.isPending || !flagKey || !environmentKey}
            type="submit"
          >
            {createExperiment.isPending ? "Creating..." : "Create experiment"}
          </Button>
        </div>
      </form>
    </div>
  )
}
