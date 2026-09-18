import { useQuery } from "@tanstack/react-query";
import { listFeatureFlags } from "../api";

export function useFeatureFlags(projectKey: string | null) {
  return useQuery({
    queryKey: ["feature-flags", projectKey],
    queryFn: () => listFeatureFlags(projectKey!),
    enabled: projectKey !== null,
  });
}
