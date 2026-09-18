import { api } from "@/api/client";
import type {
  ApprovalRequest,
  ApproveApprovalRequestRequest,
  RejectApprovalRequestRequest,
  ScheduleApprovalRequestRequest,
  SubmitApprovalRequestRequest,
} from "./types";

interface ApiResponse<T> {
  data: T;
}

export async function listApprovalRequests(projectKey: string): Promise<ApprovalRequest[]> {
  const response = await api
    .get(`projects/${projectKey}/approval-requests`)
    .json<ApiResponse<ApprovalRequest[]>>();

  return response.data;
}

export async function getApprovalRequest(
  projectKey: string,
  requestId: string,
): Promise<ApprovalRequest> {
  const response = await api
    .get(`projects/${projectKey}/approval-requests/${requestId}`)
    .json<ApiResponse<ApprovalRequest>>();

  return response.data;
}

export async function submitApprovalRequest(
  projectKey: string,
  flagKey: string,
  environmentKey: string,
  request: SubmitApprovalRequestRequest,
): Promise<ApprovalRequest> {
  const response = await api
    .post(`projects/${projectKey}/flags/${flagKey}/environments/${environmentKey}/approval-requests`, {
      json: request,
    })
    .json<ApiResponse<ApprovalRequest>>();

  return response.data;
}

export async function approveApprovalRequest(
  projectKey: string,
  requestId: string,
  request: ApproveApprovalRequestRequest,
): Promise<ApprovalRequest> {
  const response = await api
    .post(`projects/${projectKey}/approval-requests/${requestId}/approve`, { json: request })
    .json<ApiResponse<ApprovalRequest>>();

  return response.data;
}

export async function rejectApprovalRequest(
  projectKey: string,
  requestId: string,
  request: RejectApprovalRequestRequest,
): Promise<ApprovalRequest> {
  const response = await api
    .post(`projects/${projectKey}/approval-requests/${requestId}/reject`, { json: request })
    .json<ApiResponse<ApprovalRequest>>();

  return response.data;
}

export async function scheduleApprovalRequest(
  projectKey: string,
  requestId: string,
  request: ScheduleApprovalRequestRequest,
): Promise<ApprovalRequest> {
  const response = await api
    .post(`projects/${projectKey}/approval-requests/${requestId}/schedule`, { json: request })
    .json<ApiResponse<ApprovalRequest>>();

  return response.data;
}

export async function cancelApprovalRequest(
  projectKey: string,
  requestId: string,
): Promise<ApprovalRequest> {
  const response = await api
    .post(`projects/${projectKey}/approval-requests/${requestId}/cancel`)
    .json<ApiResponse<ApprovalRequest>>();

  return response.data;
}
