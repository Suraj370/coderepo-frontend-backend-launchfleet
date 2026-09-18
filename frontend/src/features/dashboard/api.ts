import { api } from "@/api/client";

interface ApiResponse<T> {
  data: T;
}

export type FlagStatus = "ACTIVE" | "RETIRED";
export type SegmentStatus = "ACTIVE" | "RETIRED";
export type ExperimentStatus = "DRAFT" | "RUNNING" | "COMPLETED" | "CANCELLED";

export interface FlagSummary {
  id: string;
  key: string;
  status: FlagStatus;
}

export interface SegmentSummary {
  id: string;
  key: string;
  status: SegmentStatus;
}

export interface ExperimentSummary {
  id: string;
  key: string;
  status: ExperimentStatus;
}

// Minimal, list-only shapes - the full flags/segments/experiments feature
// modules (with their complete types) land in later phases; the dashboard
// only ever needs each item's status to compute summary counts.

export async function listFlagSummaries(projectKey: string): Promise<FlagSummary[]> {
  const response = await api
    .get(`projects/${projectKey}/flags`)
    .json<ApiResponse<FlagSummary[]>>();

  return response.data;
}

export async function listSegmentSummaries(projectKey: string): Promise<SegmentSummary[]> {
  const response = await api
    .get(`projects/${projectKey}/segments`)
    .json<ApiResponse<SegmentSummary[]>>();

  return response.data;
}

export async function listExperimentSummaries(projectKey: string): Promise<ExperimentSummary[]> {
  const response = await api
    .get(`projects/${projectKey}/experiments`)
    .json<ApiResponse<ExperimentSummary[]>>();

  return response.data;
}

export type ActivityAction =
  | "FLAG_CREATED"
  | "FLAG_RETIRED"
  | "ENVIRONMENT_CREATED"
  | "ENVIRONMENT_RETIRED"
  | "SEGMENT_CREATED"
  | "SEGMENT_RETIRED"
  | "EXPERIMENT_CREATED"
  | "EXPERIMENT_STARTED"
  | "EXPERIMENT_COMPLETED"
  | "APPROVAL_SUBMITTED"
  | "APPROVAL_APPROVED"
  | "APPROVAL_REJECTED";

export interface ActivityEntry {
  id: string;
  actorUserId: string | null;
  actorName: string;
  action: ActivityAction;
  subjectType: string;
  subjectKey: string;
  environmentId: string | null;
  occurredAt: string;
}

export async function listActivity(projectKey: string): Promise<ActivityEntry[]> {
  const response = await api
    .get(`projects/${projectKey}/activity`)
    .json<ApiResponse<ActivityEntry[]>>();

  return response.data;
}

export interface FlagEvaluationDaySummary {
  date: string;
  count: number;
}

export interface FlagEvaluationSummary {
  totalLast7Days: number;
  percentChangeVsPriorPeriod: number;
  byDay: FlagEvaluationDaySummary[];
}

export async function getFlagEvaluationSummary(projectKey: string): Promise<FlagEvaluationSummary> {
  const response = await api
    .get(`projects/${projectKey}/flags/evaluations/summary`)
    .json<ApiResponse<FlagEvaluationSummary>>();

  return response.data;
}
