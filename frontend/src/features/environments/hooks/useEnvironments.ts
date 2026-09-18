import { useQuery } from "@tanstack/react-query";
import { listEnvironments } from "../api";

export function useEnvironments(projectKey: string | null) {
  return useQuery({
    queryKey: ["environments", projectKey],
    queryFn: () => listEnvironments(projectKey!),
    enabled: projectKey !== null,
  });
}
