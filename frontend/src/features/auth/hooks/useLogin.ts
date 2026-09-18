import { useMutation, useQueryClient } from "@tanstack/react-query";
import { initializeCsrf, login } from "../api";
import type { LoginRequest } from "../types";

export function useLogin() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (request: LoginRequest) => {
      await initializeCsrf();
      return login(request);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["auth", "session"],
      });
    },
  });
}
