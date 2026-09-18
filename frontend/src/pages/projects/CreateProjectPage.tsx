import { type FormEvent, useEffect, useState } from "react"
import { Link, useNavigate } from "@tanstack/react-router"

import { getApiErrorMessage } from "@/api/errors"
import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useCreateProject } from "@/features/projects/hooks/useCreateProject"

function slugify(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "")
}

export function CreateProjectPage() {
  const navigate = useNavigate()
  const createProject = useCreateProject()
  const [name, setName] = useState("")
  const [key, setKey] = useState("")
  const [keyEditedManually, setKeyEditedManually] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!createProject.error) {
      setErrorMessage(null)
      return
    }

    let cancelled = false

    getApiErrorMessage(createProject.error).then((message) => {
      if (!cancelled) {
        setErrorMessage(message)
      }
    })

    return () => {
      cancelled = true
    }
  }, [createProject.error])

  function handleNameChange(value: string) {
    setName(value)
    if (!keyEditedManually) {
      setKey(slugify(value))
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    createProject.mutate(
      { key, name },
      {
        onSuccess: () => {
          navigate({ to: "/projects" })
        },
      },
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <Link className="w-fit text-sm text-muted-foreground no-underline hover:underline" to="/projects">
        ← Projects
      </Link>

      <div>
        <h1 className="text-2xl font-semibold">Create a project</h1>
        <p className="text-sm text-muted-foreground">Creating a project makes you its admin.</p>
      </div>

      <form onSubmit={handleSubmit}>
        <FieldGroup>
          <Field data-invalid={errorMessage ? true : undefined}>
            <FieldLabel htmlFor="project-name">Name</FieldLabel>
            <Input
              autoFocus
              id="project-name"
              onChange={(event) => handleNameChange(event.target.value)}
              placeholder="My Project"
              required
              type="text"
              value={name}
            />
          </Field>
          <Field data-invalid={errorMessage ? true : undefined}>
            <FieldLabel htmlFor="project-key">Key</FieldLabel>
            <Input
              id="project-key"
              onChange={(event) => {
                setKeyEditedManually(true)
                setKey(event.target.value)
              }}
              placeholder="my-project"
              required
              type="text"
              value={key}
            />
          </Field>
          {errorMessage && <FieldError>{errorMessage}</FieldError>}
        </FieldGroup>

        <div className="sticky bottom-0 mt-6 flex justify-end gap-2 p-4">
          <Button onPress={() => navigate({ to: "/projects" })} type="button" variant="outline">
            Cancel
          </Button>
          <Button isDisabled={createProject.isPending} type="submit">
            {createProject.isPending ? "Creating..." : "Create project"}
          </Button>
        </div>
      </form>
    </div>
  )
}
