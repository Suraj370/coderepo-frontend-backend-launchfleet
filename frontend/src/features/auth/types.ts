export interface User {
  id: string;
  email: string;
  name: string;
  role: "VIEWER" | "EDITOR" | "ADMIN";
}

export interface Session {
  authenticated: boolean;
  user: User | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}
