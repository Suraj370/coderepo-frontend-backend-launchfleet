import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createProject } from "../api";
import type { CreateProjectRequest } from "../types";

export function useCreateProject() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateProjectRequest) => createProject(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
    },
  });
}
