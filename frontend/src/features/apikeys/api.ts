import { api } from "@/api/client";
import type { ApiKey, CreateApiKeyRequest } from "./types";

interface ApiResponse<T> {
  data: T;
}

export async function listApiKeys(projectKey: string): Promise<ApiKey[]> {
  const response = await api
    .get(`projects/${projectKey}/api-keys`)
    .json<ApiResponse<ApiKey[]>>();

  return response.data;
}

export async function createApiKey(
  projectKey: string,
  request: CreateApiKeyRequest,
): Promise<ApiKey> {
  const response = await api
    .post(`projects/${projectKey}/api-keys`, { json: request })
    .json<ApiResponse<ApiKey>>();

  return response.data;
}

export async function revokeApiKey(projectKey: string, id: string): Promise<ApiKey> {
  const response = await api
    .post(`projects/${projectKey}/api-keys/${id}/revoke`)
    .json<ApiResponse<ApiKey>>();

  return response.data;
}
