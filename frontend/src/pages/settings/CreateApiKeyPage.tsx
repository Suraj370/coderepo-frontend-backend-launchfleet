import { type FormEvent, useEffect, useState } from "react"
import { Link, useNavigate } from "@tanstack/react-router"

import { useEnvironmentContext } from "@/app/environment-context"
import { getApiErrorMessage } from "@/api/errors"
import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { useCreateApiKey } from "@/features/apikeys/hooks/useCreateApiKey"
import type { ApiKey, SdkCredentialType } from "@/features/apikeys/types"
import { useEnvironments } from "@/features/environments/hooks/useEnvironments"

export function CreateApiKeyPage() {
  const navigate = useNavigate()
  const { projectKey } = useEnvironmentContext()
  const environments = useEnvironments(projectKey)
  const createKey = useCreateApiKey(projectKey ?? "")
  const [label, setLabel] = useState("")
  const [type, setType] = useState<SdkCredentialType>("SERVER")
  const [environmentKey, setEnvironmentKey] = useState("")
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [issuedKey, setIssuedKey] = useState<ApiKey | null>(null)

  useEffect(() => {
    if (!createKey.error) {
      setErrorMessage(null)
      return
    }

    let cancelled = false

    getApiErrorMessage(createKey.error).then((message) => {
      if (!cancelled) {
        setErrorMessage(message)
      }
    })

    return () => {
      cancelled = true
    }
  }, [createKey.error])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    createKey.mutate({ environmentKey, type, label }, { onSuccess: (key) => setIssuedKey(key) })
  }

  const secretValue = issuedKey?.type === "CLIENT_SIDE" ? issuedKey.clientSideId : issuedKey?.plaintextSecret

  if (!projectKey) {
    return null
  }

  return (
    <div className="flex flex-col gap-6">
      <Link className="w-fit text-sm text-muted-foreground no-underline hover:underline" to="/settings">
        ← Settings
      </Link>

      <div>
        <h1 className="text-2xl font-semibold">
          {issuedKey ? "API key issued" : "Issue a new API key"}
        </h1>
        <p className="text-sm text-muted-foreground">SDK server keys and client-side ids for this project.</p>
      </div>

      {issuedKey ? (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-muted-foreground">
            Copy this value now - it won&apos;t be shown again.
          </p>
          <code className="break-all rounded-lg border bg-muted p-2.5 text-xs">{secretValue}</code>
          <div className="sticky bottom-0 mt-3 flex justify-end gap-2 p-4">
            <Button onPress={() => navigate({ to: "/settings" })}>Done</Button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit}>
          <FieldGroup>
            <Field data-invalid={errorMessage ? true : undefined}>
              <FieldLabel htmlFor="key-label">Label</FieldLabel>
              <Input
                autoFocus
                id="key-label"
                onChange={(event) => setLabel(event.target.value)}
                placeholder="Production server key"
                required
                type="text"
                value={label}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="key-type">Type</FieldLabel>
              <Select
                aria-label="Type"
                id="key-type"
                onSelectionChange={(value) => setType(String(value) as SdkCredentialType)}
                selectedKey={type}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem id="SERVER">Server key</SelectItem>
                  <SelectItem id="CLIENT_SIDE">Client-side id</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel htmlFor="key-environment">Environment</FieldLabel>
              <Select
                aria-label="Environment"
                id="key-environment"
                isDisabled={environments.isPending}
                onSelectionChange={(value) => setEnvironmentKey(String(value))}
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
            <Button onPress={() => navigate({ to: "/settings" })} type="button" variant="outline">
              Cancel
            </Button>
            <Button isDisabled={createKey.isPending || !environmentKey} type="submit">
              {createKey.isPending ? "Issuing..." : "Issue key"}
            </Button>
          </div>
        </form>
      )}
    </div>
  )
}
