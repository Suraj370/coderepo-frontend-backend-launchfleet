import { useQuery } from "@tanstack/react-query";

import { getFlagEvaluationSummary } from "../api";

export function useFlagEvaluationSummary(projectKey: string | null) {
  return useQuery({
    queryKey: ["dashboard", "flag-evaluation-summary", projectKey],
    queryFn: () => getFlagEvaluationSummary(projectKey!),
    enabled: projectKey !== null,
  });
}
