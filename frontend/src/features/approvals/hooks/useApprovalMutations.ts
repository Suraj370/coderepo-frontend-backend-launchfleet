import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  approveApprovalRequest,
  cancelApprovalRequest,
  rejectApprovalRequest,
  scheduleApprovalRequest,
  submitApprovalRequest,
} from "../api";
import type {
  ApproveApprovalRequestRequest,
  RejectApprovalRequestRequest,
  ScheduleApprovalRequestRequest,
  SubmitApprovalRequestRequest,
} from "../types";

function useInvalidateApprovalRequests(projectKey: string, requestId?: string) {
  const queryClient = useQueryClient();

  return () => {
    queryClient.invalidateQueries({ queryKey: ["approval-requests", projectKey], exact: true });
    if (requestId) {
      queryClient.invalidateQueries({ queryKey: ["approval-requests", projectKey, requestId] });
    }
    queryClient.invalidateQueries({ queryKey: ["feature-flags", projectKey] });
  };
}

export function useSubmitApprovalRequest(
  projectKey: string,
  flagKey: string,
  environmentKey: string,
) {
  const invalidate = useInvalidateApprovalRequests(projectKey);

  return useMutation({
    mutationFn: (request: SubmitApprovalRequestRequest) =>
      submitApprovalRequest(projectKey, flagKey, environmentKey, request),
    onSuccess: invalidate,
  });
}

export function useApproveApprovalRequest(projectKey: string, requestId: string) {
  const invalidate = useInvalidateApprovalRequests(projectKey, requestId);

  return useMutation({
    mutationFn: (request: ApproveApprovalRequestRequest) =>
      approveApprovalRequest(projectKey, requestId, request),
    onSuccess: invalidate,
  });
}

export function useRejectApprovalRequest(projectKey: string, requestId: string) {
  const invalidate = useInvalidateApprovalRequests(projectKey, requestId);

  return useMutation({
    mutationFn: (request: RejectApprovalRequestRequest) =>
      rejectApprovalRequest(projectKey, requestId, request),
    onSuccess: invalidate,
  });
}

export function useScheduleApprovalRequest(projectKey: string, requestId: string) {
  const invalidate = useInvalidateApprovalRequests(projectKey, requestId);

  return useMutation({
    mutationFn: (request: ScheduleApprovalRequestRequest) =>
      scheduleApprovalRequest(projectKey, requestId, request),
    onSuccess: invalidate,
  });
}

export function useCancelApprovalRequest(projectKey: string, requestId: string) {
  const invalidate = useInvalidateApprovalRequests(projectKey, requestId);

  return useMutation({
    mutationFn: () => cancelApprovalRequest(projectKey, requestId),
    onSuccess: invalidate,
  });
}
