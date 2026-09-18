import { useMutation, useQueryClient } from "@tanstack/react-query";
import { updateMe } from "../api";
import type { UpdateProfileRequest } from "../types";

export function useUpdateProfile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: UpdateProfileRequest) => updateMe(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users", "me"] });
      queryClient.invalidateQueries({ queryKey: ["auth", "session"] });
    },
  });
}
