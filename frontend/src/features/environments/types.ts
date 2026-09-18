export type EnvironmentStatus = "ACTIVE" | "RETIRED";

export interface Environment {
  id: string;
  projectId: string;
  key: string;
  name: string;
  status: EnvironmentStatus;
  createdAt: string;
}

export interface CreateEnvironmentRequest {
  key: string;
  name: string;
}
