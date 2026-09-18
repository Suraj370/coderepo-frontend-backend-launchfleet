import { api } from "@/api/client";
import type {
  LoginRequest,
  RegisterRequest,
  Session,
  User,
} from "./types";

interface ApiResponse<T> {
  data: T;
}

export async function getSession(): Promise<Session> {
  const response = await api
    .get("auth/session")
    .json<ApiResponse<Session>>();

  return response.data;
}

export async function login(
  request: LoginRequest,
): Promise<User> {
  const response = await api
    .post("auth/login", {
      json: request,
    })
    .json<ApiResponse<User>>();

  return response.data;
}

export async function register(
  request: RegisterRequest,
): Promise<User> {
  const response = await api
    .post("auth/register", {
      json: request,
    })
    .json<ApiResponse<User>>();

  return response.data;
}

export async function logout(): Promise<void> {
  await api.post("auth/logout");
}

export async function initializeCsrf(): Promise<void> {
  await api.get("auth/session", {
    throwHttpErrors: false,
  });
}
