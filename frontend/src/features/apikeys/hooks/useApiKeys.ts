import { useQuery } from "@tanstack/react-query";
import { listApiKeys } from "../api";

export function useApiKeys(projectKey: string | null) {
  return useQuery({
    queryKey: ["api-keys", projectKey],
    queryFn: () => listApiKeys(projectKey!),
    enabled: projectKey !== null,
  });
}
