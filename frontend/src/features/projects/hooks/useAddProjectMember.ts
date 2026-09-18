import { useMutation, useQueryClient } from "@tanstack/react-query";
import { addProjectMember } from "../api";
import type { AddProjectMemberRequest } from "../types";

export function useAddProjectMember(projectKey: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: AddProjectMemberRequest) => addProjectMember(projectKey, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["project-members", projectKey] });
    },
  });
}
