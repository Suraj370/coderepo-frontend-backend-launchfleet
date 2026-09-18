import { useMutation, useQueryClient } from "@tanstack/react-query";
import { startExperiment, stopExperiment, updateExperiment } from "../api";
import type { UpdateExperimentRequest } from "../types";

function useInvalidateExperiment(projectKey: string, experimentKey: string) {
  const queryClient = useQueryClient();

  return () => {
    queryClient.invalidateQueries({ queryKey: ["experiments", projectKey, experimentKey] });
    queryClient.invalidateQueries({ queryKey: ["experiments", projectKey], exact: true });
  };
}

export function useUpdateExperiment(projectKey: string, experimentKey: string) {
  const invalidate = useInvalidateExperiment(projectKey, experimentKey);

  return useMutation({
    mutationFn: (request: UpdateExperimentRequest) =>
      updateExperiment(projectKey, experimentKey, request),
    onSuccess: invalidate,
  });
}

export function useStartExperiment(projectKey: string, experimentKey: string) {
  const invalidate = useInvalidateExperiment(projectKey, experimentKey);

  return useMutation({
    mutationFn: () => startExperiment(projectKey, experimentKey),
    onSuccess: invalidate,
  });
}

export function useStopExperiment(projectKey: string, experimentKey: string) {
  const invalidate = useInvalidateExperiment(projectKey, experimentKey);

  return useMutation({
    mutationFn: () => stopExperiment(projectKey, experimentKey),
    onSuccess: invalidate,
  });
}
