import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createApiKey } from "../api";
import type { CreateApiKeyRequest } from "../types";

export function useCreateApiKey(projectKey: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateApiKeyRequest) => createApiKey(projectKey, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["api-keys", projectKey] });
    },
  });
}
