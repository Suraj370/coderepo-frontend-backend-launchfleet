import { useQuery } from "@tanstack/react-query";
import { listProjectMembers } from "../api";

export function useProjectMembers(projectKey: string | null) {
  return useQuery({
    queryKey: ["project-members", projectKey],
    queryFn: () => listProjectMembers(projectKey!),
    enabled: projectKey !== null,
  });
}
