export interface User {
  id: string;
  name: string;
  email: string;
}

export interface UpdateProfileRequest {
  name: string;
}
