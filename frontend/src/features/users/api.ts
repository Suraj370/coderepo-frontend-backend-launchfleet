import { api } from "@/api/client";
import type { UpdateProfileRequest, User } from "./types";

interface ApiResponse<T> {
  data: T;
}

export async function getMe(): Promise<User> {
  const response = await api.get("users/me").json<ApiResponse<User>>();

  return response.data;
}

export async function updateMe(request: UpdateProfileRequest): Promise<User> {
  const response = await api
    .patch("users/me", { json: request })
    .json<ApiResponse<User>>();

  return response.data;
}
