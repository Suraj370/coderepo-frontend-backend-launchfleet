export type SdkCredentialType = "SERVER" | "CLIENT_SIDE";

export interface ApiKey {
  id: string;
  environmentKey: string;
  type: SdkCredentialType;
  label: string;
  clientSideId: string | null;
  active: boolean;
  createdAt: string;
  revokedAt: string | null;
  expiresAt: string | null;
  plaintextSecret?: string;
}

export interface CreateApiKeyRequest {
  environmentKey: string;
  type: SdkCredentialType;
  label: string;
}
