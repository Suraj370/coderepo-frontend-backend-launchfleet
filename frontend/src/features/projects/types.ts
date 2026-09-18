export type ProjectRole = "VIEWER" | "EDITOR" | "ADMIN";

export interface ProjectMembership {
  id: string;
  key: string;
  name: string;
  role: ProjectRole;
  createdAt: string;
}

export interface CreateProjectRequest {
  key: string;
  name: string;
}

export interface RenameProjectRequest {
  name: string;
}

export interface ProjectMember {
  id: string;
  userId: string;
  name: string;
  email: string;
  role: ProjectRole;
  createdAt: string;
}

export interface AddProjectMemberRequest {
  email: string;
  role: ProjectRole;
}
