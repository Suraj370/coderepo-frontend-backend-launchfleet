import { useQuery } from "@tanstack/react-query";
import { getMe } from "../api";

export function useMe() {
  return useQuery({
    queryKey: ["users", "me"],
    queryFn: getMe,
  });
}
