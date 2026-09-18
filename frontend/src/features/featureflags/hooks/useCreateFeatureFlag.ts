import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createFeatureFlag } from "../api";
import type { CreateFeatureFlagRequest } from "../types";

export function useCreateFeatureFlag(projectKey: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateFeatureFlagRequest) => createFeatureFlag(projectKey, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["feature-flags", projectKey] });
    },
  });
}
