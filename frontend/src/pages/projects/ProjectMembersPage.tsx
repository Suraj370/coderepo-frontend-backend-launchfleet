import { type FormEvent, useEffect, useState } from "react"
import { Trash2, UserPlus } from "lucide-react"
import { Link, useParams } from "@tanstack/react-router"

import { getApiErrorMessage } from "@/api/errors"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { useAddProjectMember } from "@/features/projects/hooks/useAddProjectMember"
import { useProjectMembers } from "@/features/projects/hooks/useProjectMembers"
import { useProjects } from "@/features/projects/hooks/useProjects"
import { useRemoveProjectMember } from "@/features/projects/hooks/useRemoveProjectMember"
import { useUpdateProjectMemberRole } from "@/features/projects/hooks/useUpdateProjectMemberRole"
import type { ProjectRole } from "@/features/projects/types"

const ROLES: ProjectRole[] = ["VIEWER", "EDITOR", "ADMIN"]

export function ProjectMembersPage() {
  const { projectKey } = useParams({ from: "/_app/projects_/$projectKey/members" })
  const projects = useProjects()
  const members = useProjectMembers(projectKey)
  const removeMember = useRemoveProjectMember(projectKey)
  const updateRole = useUpdateProjectMemberRole(projectKey)

  const project = projects.data?.find((p) => p.key === projectKey)
  const isAdmin = project?.role === "ADMIN"

  return (
    <div className="flex flex-col gap-6">
      <Link className="w-fit text-sm text-muted-foreground no-underline hover:underline" to="/projects">
        ← Projects
      </Link>

      <div>
        <h1 className="text-2xl font-semibold">{project?.name ?? projectKey} team</h1>
        <p className="text-sm text-muted-foreground">Manage who has access to this project.</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Members</CardTitle>
          {members.isError && (
            <CardDescription className="text-destructive">
              Something went wrong loading the team.
            </CardDescription>
          )}
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {members.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : (
            <ul className="flex flex-col gap-2">
              {members.data?.map((member) => (
                <li className="flex items-center gap-2 text-sm" key={member.id}>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{member.name}</p>
                    <p className="truncate text-xs text-muted-foreground">{member.email}</p>
                  </div>
                  {isAdmin ? (
                    <Select
                      aria-label={`Role for ${member.name}`}
                      isDisabled={updateRole.isPending}
                      onSelectionChange={(role) =>
                        updateRole.mutate({
                          membershipId: member.id,
                          role: String(role) as ProjectRole,
                        })
                      }
                      selectedKey={member.role}
                    >
                      <SelectTrigger className="w-24" size="sm">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {ROLES.map((role) => (
                          <SelectItem id={role} key={role}>
                            {role}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  ) : (
                    <span className="text-xs text-muted-foreground">{member.role}</span>
                  )}
                  {isAdmin && (
                    <Button
                      aria-label={`Remove ${member.name}`}
                      isDisabled={removeMember.isPending}
                      onPress={() => removeMember.mutate(member.id)}
                      size="icon-sm"
                      variant="ghost"
                    >
                      <Trash2 />
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}

          {isAdmin && <InviteMemberForm projectKey={projectKey} />}
        </CardContent>
      </Card>
    </div>
  )
}

function InviteMemberForm({ projectKey }: { projectKey: string }) {
  const addMember = useAddProjectMember(projectKey)
  const [email, setEmail] = useState("")
  const [role, setRole] = useState<ProjectRole>("VIEWER")
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!addMember.error) {
      setErrorMessage(null)
      return
    }

    let cancelled = false

    getApiErrorMessage(addMember.error).then((message) => {
      if (!cancelled) {
        setErrorMessage(message)
      }
    })

    return () => {
      cancelled = true
    }
  }, [addMember.error])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    addMember.mutate(
      { email, role },
      {
        onSuccess: () => {
          setEmail("")
          setRole("VIEWER")
        },
      },
    )
  }

  return (
    <form className="flex flex-col gap-2 border-t pt-4" onSubmit={handleSubmit}>
      <Field data-invalid={errorMessage ? true : undefined}>
        <FieldLabel htmlFor="invite-email">Invite by email</FieldLabel>
        <div className="flex gap-2">
          <Input
            className="flex-1"
            id="invite-email"
            onChange={(event) => setEmail(event.target.value)}
            placeholder="teammate@company.com"
            required
            type="email"
            value={email}
          />
          <Select
            aria-label="Role"
            onSelectionChange={(key) => setRole(String(key) as ProjectRole)}
            selectedKey={role}
          >
            <SelectTrigger className="w-24" size="sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {ROLES.map((r) => (
                <SelectItem id={r} key={r}>
                  {r}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button isDisabled={addMember.isPending} type="submit">
            <UserPlus /> {addMember.isPending ? "Inviting..." : "Invite"}
          </Button>
        </div>
        {errorMessage && <FieldError>{errorMessage}</FieldError>}
      </Field>
    </form>
  )
}
