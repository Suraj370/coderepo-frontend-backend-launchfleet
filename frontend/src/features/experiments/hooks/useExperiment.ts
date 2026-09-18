import { useQuery } from "@tanstack/react-query";
import { getExperiment } from "../api";

export function useExperiment(projectKey: string | null, experimentKey: string) {
  return useQuery({
    queryKey: ["experiments", projectKey, experimentKey],
    queryFn: () => getExperiment(projectKey!, experimentKey),
    enabled: projectKey !== null,
  });
}
