import { useQuery } from "@tanstack/react-query";
import { listActivity } from "../api";

export function useActivity(projectKey: string | null) {
  return useQuery({
    queryKey: ["dashboard", "activity", projectKey],
    queryFn: () => listActivity(projectKey!),
    enabled: projectKey !== null,
  });
}
