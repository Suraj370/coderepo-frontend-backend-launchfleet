import { useMutation, useQueryClient } from "@tanstack/react-query";
import { renameProject } from "../api";

export function useRenameProject(projectKey: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (name: string) => renameProject(projectKey, { name }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
    },
  });
}
