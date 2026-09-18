import { api } from "@/api/client";
import type { CreateEnvironmentRequest, Environment } from "./types";

interface ApiResponse<T> {
  data: T;
}

export async function listEnvironments(projectKey: string): Promise<Environment[]> {
  const response = await api
    .get(`projects/${projectKey}/environments`)
    .json<ApiResponse<Environment[]>>();

  return response.data;
}

export async function createEnvironment(
  projectKey: string,
  request: CreateEnvironmentRequest,
): Promise<Environment> {
  const response = await api
    .post(`projects/${projectKey}/environments`, { json: request })
    .json<ApiResponse<Environment>>();

  return response.data;
}

export async function retireEnvironment(
  projectKey: string,
  environmentKey: string,
): Promise<Environment> {
  const response = await api
    .post(`projects/${projectKey}/environments/${environmentKey}/retire`)
    .json<ApiResponse<Environment>>();

  return response.data;
}
