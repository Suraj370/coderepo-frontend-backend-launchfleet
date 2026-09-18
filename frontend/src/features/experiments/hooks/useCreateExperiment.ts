import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createExperiment } from "../api";
import type { CreateExperimentRequest } from "../types";

export function useCreateExperiment(projectKey: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateExperimentRequest) => createExperiment(projectKey, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["experiments", projectKey], exact: true });
    },
  });
}
