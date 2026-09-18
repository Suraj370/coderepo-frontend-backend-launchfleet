import { api } from "@/api/client";
import type {
  CreateExperimentRequest,
  Experiment,
  ExperimentVariantMetrics,
  UpdateExperimentRequest,
} from "./types";

interface ApiResponse<T> {
  data: T;
}

export async function listExperiments(projectKey: string): Promise<Experiment[]> {
  const response = await api
    .get(`projects/${projectKey}/experiments`)
    .json<ApiResponse<Experiment[]>>();

  return response.data;
}

export async function getExperiment(
  projectKey: string,
  experimentKey: string,
): Promise<Experiment> {
  const response = await api
    .get(`projects/${projectKey}/experiments/${experimentKey}`)
    .json<ApiResponse<Experiment>>();

  return response.data;
}

export async function createExperiment(
  projectKey: string,
  request: CreateExperimentRequest,
): Promise<Experiment> {
  const response = await api
    .post(`projects/${projectKey}/experiments`, { json: request })
    .json<ApiResponse<Experiment>>();

  return response.data;
}

export async function updateExperiment(
  projectKey: string,
  experimentKey: string,
  request: UpdateExperimentRequest,
): Promise<Experiment> {
  const response = await api
    .patch(`projects/${projectKey}/experiments/${experimentKey}`, { json: request })
    .json<ApiResponse<Experiment>>();

  return response.data;
}

export async function startExperiment(
  projectKey: string,
  experimentKey: string,
): Promise<Experiment> {
  const response = await api
    .post(`projects/${projectKey}/experiments/${experimentKey}/start`)
    .json<ApiResponse<Experiment>>();

  return response.data;
}

export async function stopExperiment(
  projectKey: string,
  experimentKey: string,
): Promise<Experiment> {
  const response = await api
    .post(`projects/${projectKey}/experiments/${experimentKey}/stop`)
    .json<ApiResponse<Experiment>>();

  return response.data;
}

export async function getExperimentMetrics(
  projectKey: string,
  experimentKey: string,
): Promise<ExperimentVariantMetrics[]> {
  const response = await api
    .get(`projects/${projectKey}/experiments/${experimentKey}/metrics`)
    .json<ApiResponse<ExperimentVariantMetrics[]>>();

  return response.data;
}
