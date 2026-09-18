import { useQuery } from "@tanstack/react-query";
import { listApprovalRequests } from "../api";

export function useApprovalRequests(projectKey: string | null) {
  return useQuery({
    queryKey: ["approval-requests", projectKey],
    queryFn: () => listApprovalRequests(projectKey!),
    enabled: projectKey !== null,
  });
}
