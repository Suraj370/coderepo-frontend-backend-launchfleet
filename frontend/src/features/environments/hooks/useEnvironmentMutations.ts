import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createEnvironment, retireEnvironment } from "../api";
import type { CreateEnvironmentRequest } from "../types";

function useInvalidateEnvironments(projectKey: string) {
  const queryClient = useQueryClient();

  return () => queryClient.invalidateQueries({ queryKey: ["environments", projectKey] });
}

export function useCreateEnvironment(projectKey: string) {
  const invalidate = useInvalidateEnvironments(projectKey);

  return useMutation({
    mutationFn: (request: CreateEnvironmentRequest) => createEnvironment(projectKey, request),
    onSuccess: invalidate,
  });
}

export function useRetireEnvironment(projectKey: string) {
  const invalidate = useInvalidateEnvironments(projectKey);

  return useMutation({
    mutationFn: (environmentKey: string) => retireEnvironment(projectKey, environmentKey),
    onSuccess: invalidate,
  });
}
