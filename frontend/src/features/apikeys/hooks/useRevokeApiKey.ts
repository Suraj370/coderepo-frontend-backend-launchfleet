import { useMutation, useQueryClient } from "@tanstack/react-query";
import { revokeApiKey } from "../api";

export function useRevokeApiKey(projectKey: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => revokeApiKey(projectKey, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["api-keys", projectKey] });
    },
  });
}
