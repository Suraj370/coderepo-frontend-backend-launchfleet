import { useMutation, useQueryClient } from "@tanstack/react-query";
import { initializeCsrf, register } from "../api";
import type { RegisterRequest } from "../types";

export function useRegister() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (request: RegisterRequest) => {
      await initializeCsrf();
      return register(request);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["auth", "session"],
      });
    },
  });
}
