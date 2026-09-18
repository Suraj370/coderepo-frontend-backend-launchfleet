import { useQuery } from "@tanstack/react-query";
import { getApprovalRequest } from "../api";

export function useApprovalRequest(projectKey: string | null, requestId: string) {
  return useQuery({
    queryKey: ["approval-requests", projectKey, requestId],
    queryFn: () => getApprovalRequest(projectKey!, requestId),
    enabled: projectKey !== null,
  });
}
