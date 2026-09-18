import { useQuery } from "@tanstack/react-query";
import { listSegments } from "../api";

export function useSegments(projectKey: string | null) {
  return useQuery({
    queryKey: ["segments", projectKey],
    queryFn: () => listSegments(projectKey!),
    enabled: projectKey !== null,
  });
}
