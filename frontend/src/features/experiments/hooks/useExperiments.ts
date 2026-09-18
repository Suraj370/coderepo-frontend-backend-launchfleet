import { useQuery } from "@tanstack/react-query";
import { listExperiments } from "../api";

export function useExperiments(projectKey: string | null) {
  return useQuery({
    queryKey: ["experiments", projectKey],
    queryFn: () => listExperiments(projectKey!),
    enabled: projectKey !== null,
  });
}
