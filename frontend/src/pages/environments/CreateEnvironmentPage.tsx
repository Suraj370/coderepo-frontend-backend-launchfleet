import { type FormEvent, useEffect, useState } from "react"
import { Link, useNavigate } from "@tanstack/react-router"

import { useEnvironmentContext } from "@/app/environment-context"
import { getApiErrorMessage } from "@/api/errors"
import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useCreateEnvironment } from "@/features/environments/hooks/useEnvironmentMutations"

function slugify(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "")
}

export function CreateEnvironmentPage() {
  const navigate = useNavigate()
  const { projectKey } = useEnvironmentContext()
  const createEnvironment = useCreateEnvironment(projectKey ?? "")
  const [name, setName] = useState("")
  const [key, setKey] = useState("")
  const [keyEditedManually, setKeyEditedManually] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!createEnvironment.error) {
      setErrorMessage(null)
      return
    }

    let cancelled = false

    getApiErrorMessage(createEnvironment.error).then((message) => {
      if (!cancelled) {
        setErrorMessage(message)
      }
    })

    return () => {
      cancelled = true
    }
  }, [createEnvironment.error])

  function handleNameChange(value: string) {
    setName(value)
    if (!keyEditedManually) {
      setKey(slugify(value))
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    createEnvironment.mutate(
      { key, name },
      {
        onSuccess: () => {
          navigate({ to: "/environments" })
        },
      },
    )
  }

  if (!projectKey) {
    return null
  }

  return (
    <div className="flex flex-col gap-6">
      <Link className="w-fit text-sm text-muted-foreground no-underline hover:underline" to="/environments">
        ← Environments
      </Link>

      <div>
        <h1 className="text-2xl font-semibold">Create an environment</h1>
        <p className="text-sm text-muted-foreground">
          Every flag gets one configuration per environment.
        </p>
      </div>

      <form onSubmit={handleSubmit}>
        <FieldGroup>
          <Field data-invalid={errorMessage ? true : undefined}>
            <FieldLabel htmlFor="environment-name">Name</FieldLabel>
            <Input
              autoFocus
              id="environment-name"
              onChange={(event) => handleNameChange(event.target.value)}
              placeholder="Staging"
              required
              type="text"
              value={name}
            />
          </Field>
          <Field data-invalid={errorMessage ? true : undefined}>
            <FieldLabel htmlFor="environment-key">Key</FieldLabel>
            <Input
              id="environment-key"
              onChange={(event) => {
                setKeyEditedManually(true)
                setKey(event.target.value)
              }}
              placeholder="staging"
              required
              type="text"
              value={key}
            />
          </Field>
          {errorMessage && <FieldError>{errorMessage}</FieldError>}
        </FieldGroup>

        <div className="sticky bottom-0 mt-6 flex justify-end gap-2 p-4">
          <Button onPress={() => navigate({ to: "/environments" })} type="button" variant="outline">
            Cancel
          </Button>
          <Button isDisabled={createEnvironment.isPending} type="submit">
            {createEnvironment.isPending ? "Creating..." : "Create environment"}
          </Button>
        </div>
      </form>
    </div>
  )
}
