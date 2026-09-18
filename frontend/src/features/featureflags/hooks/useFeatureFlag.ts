import { useQuery } from "@tanstack/react-query";
import { getFeatureFlag } from "../api";

export function useFeatureFlag(projectKey: string | null, flagKey: string) {
  return useQuery({
    queryKey: ["feature-flags", projectKey, flagKey],
    queryFn: () => getFeatureFlag(projectKey!, flagKey),
    enabled: projectKey !== null,
  });
}
