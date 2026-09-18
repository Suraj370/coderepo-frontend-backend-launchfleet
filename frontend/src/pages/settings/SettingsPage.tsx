import { type FormEvent, useEffect, useState } from "react"
import { Plus } from "lucide-react"
import { Link } from "@tanstack/react-router"

import { useEnvironmentContext } from "@/app/environment-context"
import { getApiErrorMessage } from "@/api/errors"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useApiKeys } from "@/features/apikeys/hooks/useApiKeys"
import { useRevokeApiKey } from "@/features/apikeys/hooks/useRevokeApiKey"
import { useMe } from "@/features/users/hooks/useMe"
import { useUpdateProfile } from "@/features/users/hooks/useUpdateProfile"

export function SettingsPage() {
  const { projectKey, projects } = useEnvironmentContext()
  const isAdmin = projects.find((project) => project.key === projectKey)?.role === "ADMIN"

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Settings</h1>
        <p className="text-sm text-muted-foreground">Your profile and this project's API keys.</p>
      </div>

      <Tabs defaultSelectedKey="profile">
        <TabsList>
          <TabsTrigger id="profile">Profile</TabsTrigger>
          <TabsTrigger id="api-keys">API Keys</TabsTrigger>
        </TabsList>
        <TabsContent className="mt-4" id="profile">
          <ProfileCard />
        </TabsContent>
        <TabsContent className="mt-4" id="api-keys">
          {projectKey && <ApiKeysCard isAdmin={isAdmin} projectKey={projectKey} />}
        </TabsContent>
      </Tabs>
    </div>
  )
}

function ProfileCard() {
  const me = useMe()
  const updateProfile = useUpdateProfile()
  const [name, setName] = useState("")
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const displayName = name || me.data?.name || ""

  useEffect(() => {
    if (!updateProfile.error) {
      setErrorMessage(null)
      return
    }

    let cancelled = false

    getApiErrorMessage(updateProfile.error).then((message) => {
      if (!cancelled) {
        setErrorMessage(message)
      }
    })

    return () => {
      cancelled = true
    }
  }, [updateProfile.error])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    updateProfile.mutate({ name: displayName })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Profile</CardTitle>
      </CardHeader>
      <CardContent>
        {me.isPending ? (
          <div className="flex flex-col gap-2">
            <Skeleton className="h-8 w-full max-w-sm" />
            <Skeleton className="h-8 w-full max-w-sm" />
          </div>
        ) : (
          <form className="max-w-sm" onSubmit={handleSubmit}>
            <FieldGroup>
              <Field data-invalid={errorMessage ? true : undefined}>
                <FieldLabel htmlFor="profile-name">Name</FieldLabel>
                <Input
                  id="profile-name"
                  onChange={(event) => setName(event.target.value)}
                  required
                  type="text"
                  value={displayName}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="profile-email">Email</FieldLabel>
                <Input disabled id="profile-email" type="email" value={me.data?.email ?? ""} />
              </Field>
              {errorMessage && <FieldError>{errorMessage}</FieldError>}
              <Button className="w-fit" isDisabled={updateProfile.isPending} type="submit">
                {updateProfile.isPending ? "Saving..." : "Save"}
              </Button>
            </FieldGroup>
          </form>
        )}
      </CardContent>
    </Card>
  )
}

function ApiKeysCard({ projectKey, isAdmin }: { projectKey: string; isAdmin: boolean }) {
  const apiKeys = useApiKeys(projectKey)
  const revokeKey = useRevokeApiKey(projectKey)

  if (!isAdmin) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>API keys</CardTitle>
          <CardDescription>Only project admins can view and manage API keys.</CardDescription>
        </CardHeader>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <div>
          <CardTitle>API keys</CardTitle>
          <CardDescription>SDK server keys and client-side ids for this project.</CardDescription>
        </div>
        <Link className={buttonVariants({ size: "sm" })} to="/settings/api-keys/new">
          <Plus /> New API key
        </Link>
      </CardHeader>
      <CardContent>
        {apiKeys.isError && (
          <p className="text-sm text-destructive">Something went wrong loading API keys.</p>
        )}
        {apiKeys.isPending ? (
          <div className="flex flex-col gap-2">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        ) : apiKeys.data && apiKeys.data.length > 0 ? (
          <Table aria-label="API keys">
            <TableHeader>
              <TableHead isRowHeader>Label</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Environment</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>{""}</TableHead>
            </TableHeader>
            <TableBody items={apiKeys.data}>
              {(key) => (
                <TableRow id={key.id}>
                  <TableCell>{key.label}</TableCell>
                  <TableCell className="text-muted-foreground">{key.type}</TableCell>
                  <TableCell className="text-muted-foreground">{key.environmentKey}</TableCell>
                  <TableCell>
                    <Badge variant={key.active ? "default" : "secondary"}>
                      {key.active ? "Active" : "Revoked"}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {key.active && (
                      <Button
                        isDisabled={revokeKey.isPending}
                        onPress={() => revokeKey.mutate(key.id)}
                        size="sm"
                        variant="outline"
                      >
                        Revoke
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        ) : (
          <p className="py-8 text-center text-sm text-muted-foreground">No API keys yet.</p>
        )}
      </CardContent>
    </Card>
  )
}
