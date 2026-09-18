import { useQuery } from "@tanstack/react-query";
import { getExperimentMetrics } from "../api";

export function useExperimentMetrics(projectKey: string | null, experimentKey: string) {
  return useQuery({
    queryKey: ["experiments", projectKey, experimentKey, "metrics"],
    queryFn: () => getExperimentMetrics(projectKey!, experimentKey),
    enabled: projectKey !== null,
  });
}
