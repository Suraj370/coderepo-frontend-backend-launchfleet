import { api } from "@/api/client";
import type {
  CreateFeatureFlagRequest,
  FeatureFlag,
  RolloutRequest,
  TargetingRuleRequest,
  UpdateFeatureFlagRequest,
  UpdateFlagEnvironmentConfigRequest,
} from "./types";

interface ApiResponse<T> {
  data: T;
}

export async function listFeatureFlags(projectKey: string): Promise<FeatureFlag[]> {
  const response = await api
    .get(`projects/${projectKey}/flags`)
    .json<ApiResponse<FeatureFlag[]>>();

  return response.data;
}

export async function getFeatureFlag(
  projectKey: string,
  flagKey: string,
): Promise<FeatureFlag> {
  const response = await api
    .get(`projects/${projectKey}/flags/${flagKey}`)
    .json<ApiResponse<FeatureFlag>>();

  return response.data;
}

export async function createFeatureFlag(
  projectKey: string,
  request: CreateFeatureFlagRequest,
): Promise<FeatureFlag> {
  const response = await api
    .post(`projects/${projectKey}/flags`, {
      json: request,
    })
    .json<ApiResponse<FeatureFlag>>();

  return response.data;
}

export async function updateFeatureFlag(
  projectKey: string,
  flagKey: string,
  request: UpdateFeatureFlagRequest,
): Promise<FeatureFlag> {
  const response = await api
    .patch(`projects/${projectKey}/flags/${flagKey}`, {
      json: request,
    })
    .json<ApiResponse<FeatureFlag>>();

  return response.data;
}

export async function retireFeatureFlag(
  projectKey: string,
  flagKey: string,
): Promise<FeatureFlag> {
  const response = await api
    .post(`projects/${projectKey}/flags/${flagKey}/retire`)
    .json<ApiResponse<FeatureFlag>>();

  return response.data;
}

export async function updateFlagEnvironmentConfig(
  projectKey: string,
  flagKey: string,
  environmentKey: string,
  request: UpdateFlagEnvironmentConfigRequest,
): Promise<FeatureFlag> {
  const response = await api
    .patch(`projects/${projectKey}/flags/${flagKey}/environments/${environmentKey}`, {
      json: request,
    })
    .json<ApiResponse<FeatureFlag>>();

  return response.data;
}

export async function addTargetingRule(
  projectKey: string,
  flagKey: string,
  environmentKey: string,
  request: TargetingRuleRequest,
): Promise<FeatureFlag> {
  const response = await api
    .post(
      `projects/${projectKey}/flags/${flagKey}/environments/${environmentKey}/targeting-rules`,
      { json: request },
    )
    .json<ApiResponse<FeatureFlag>>();

  return response.data;
}

export async function updateTargetingRule(
  projectKey: string,
  flagKey: string,
  environmentKey: string,
  ruleId: string,
  request: TargetingRuleRequest,
): Promise<FeatureFlag> {
  const response = await api
    .patch(
      `projects/${projectKey}/flags/${flagKey}/environments/${environmentKey}/targeting-rules/${ruleId}`,
      { json: request },
    )
    .json<ApiResponse<FeatureFlag>>();

  return response.data;
}

export async function removeTargetingRule(
  projectKey: string,
  flagKey: string,
  environmentKey: string,
  ruleId: string,
): Promise<FeatureFlag> {
  const response = await api
    .delete(
      `projects/${projectKey}/flags/${flagKey}/environments/${environmentKey}/targeting-rules/${ruleId}`,
    )
    .json<ApiResponse<FeatureFlag>>();

  return response.data;
}

export async function setRollout(
  projectKey: string,
  flagKey: string,
  environmentKey: string,
  request: RolloutRequest,
): Promise<FeatureFlag> {
  const response = await api
    .put(`projects/${projectKey}/flags/${flagKey}/environments/${environmentKey}/rollout`, {
      json: request,
    })
    .json<ApiResponse<FeatureFlag>>();

  return response.data;
}

export async function removeRollout(
  projectKey: string,
  flagKey: string,
  environmentKey: string,
): Promise<FeatureFlag> {
  const response = await api
    .delete(`projects/${projectKey}/flags/${flagKey}/environments/${environmentKey}/rollout`)
    .json<ApiResponse<FeatureFlag>>();

  return response.data;
}
