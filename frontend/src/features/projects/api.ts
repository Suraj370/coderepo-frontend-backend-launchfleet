import { api } from "@/api/client";
import type {
  AddProjectMemberRequest,
  CreateProjectRequest,
  ProjectMember,
  ProjectMembership,
  ProjectRole,
} from "./types";

interface ApiResponse<T> {
  data: T;
}

export async function listProjects(): Promise<ProjectMembership[]> {
  const response = await api.get("projects").json<ApiResponse<ProjectMembership[]>>();

  return response.data;
}

export async function createProject(
  request: CreateProjectRequest,
): Promise<ProjectMembership> {
  const response = await api
    .post("projects", {
      json: request,
    })
    .json<ApiResponse<ProjectMembership>>();

  return response.data;
}

export async function listProjectMembers(projectKey: string): Promise<ProjectMember[]> {
  const response = await api
    .get(`projects/${projectKey}/members`)
    .json<ApiResponse<ProjectMember[]>>();

  return response.data;
}

export async function addProjectMember(
  projectKey: string,
  request: AddProjectMemberRequest,
): Promise<ProjectMember> {
  const response = await api
    .post(`projects/${projectKey}/members`, {
      json: request,
    })
    .json<ApiResponse<ProjectMember>>();

  return response.data;
}

export async function updateProjectMemberRole(
  projectKey: string,
  membershipId: string,
  role: ProjectRole,
): Promise<ProjectMember> {
  const response = await api
    .patch(`projects/${projectKey}/members/${membershipId}`, {
      json: { role },
    })
    .json<ApiResponse<ProjectMember>>();

  return response.data;
}

export async function removeProjectMember(
  projectKey: string,
  membershipId: string,
): Promise<void> {
  await api.delete(`projects/${projectKey}/members/${membershipId}`);
}
