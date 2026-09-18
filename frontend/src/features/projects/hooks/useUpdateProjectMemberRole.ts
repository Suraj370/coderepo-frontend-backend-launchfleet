import { useMutation, useQueryClient } from "@tanstack/react-query";
import { updateProjectMemberRole } from "../api";
import type { ProjectRole } from "../types";

export function useUpdateProjectMemberRole(projectKey: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ membershipId, role }: { membershipId: string; role: ProjectRole }) =>
      updateProjectMemberRole(projectKey, membershipId, role),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["project-members", projectKey] });
    },
  });
}
