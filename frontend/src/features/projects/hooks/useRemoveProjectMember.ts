import { useMutation, useQueryClient } from "@tanstack/react-query";
import { removeProjectMember } from "../api";

export function useRemoveProjectMember(projectKey: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (membershipId: string) => removeProjectMember(projectKey, membershipId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["project-members", projectKey] });
    },
  });
}
