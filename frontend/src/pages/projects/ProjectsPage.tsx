import { Plus, UsersRound } from "lucide-react"
import { Link } from "@tanstack/react-router"

import { buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useProjects } from "@/features/projects/hooks/useProjects"

export function ProjectsPage() {
  const projects = useProjects()

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Projects</h1>
          <p className="text-sm text-muted-foreground">
            Projects you&apos;re a member of. Creating a project makes you its admin.
          </p>
        </div>
        <Link className={buttonVariants()} to="/projects/new">
          <Plus /> New project
        </Link>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Your projects</CardTitle>
          {projects.isError && (
            <CardDescription className="text-destructive">
              Something went wrong loading your projects.
            </CardDescription>
          )}
        </CardHeader>
        <CardContent>
          {projects.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : projects.data && projects.data.length > 0 ? (
            <Table aria-label="Projects">
              <TableHeader>
                <TableHead isRowHeader>Name</TableHead>
                <TableHead>Key</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Created</TableHead>
                <TableHead>{""}</TableHead>
              </TableHeader>
              <TableBody items={projects.data}>
                {(project) => (
                  <TableRow id={project.id}>
                    <TableCell>{project.name}</TableCell>
                    <TableCell className="text-muted-foreground">{project.key}</TableCell>
                    <TableCell>{project.role}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {new Date(project.createdAt).toLocaleDateString()}
                    </TableCell>
                    <TableCell>
                      <Link
                        className={buttonVariants({ size: "sm", variant: "outline" })}
                        params={{ projectKey: project.key }}
                        to="/projects/$projectKey/members"
                      >
                        <UsersRound /> Members
                      </Link>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          ) : (
            <p className="py-8 text-center text-sm text-muted-foreground">
              No projects yet. Create your first one to get started.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
