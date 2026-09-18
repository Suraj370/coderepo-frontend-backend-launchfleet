import { HTTPError } from "ky";

export interface ApiErrorBody {
  code: string;
  message: string;
  details?: Record<string, string>;
}

export async function getApiErrorMessage(
  error: unknown,
  fallback = "Something went wrong. Please try again.",
): Promise<string> {
  if (error instanceof HTTPError) {
    try {
      const body = await error.response.json<{ error?: ApiErrorBody }>();

      if (body?.error?.message) {
        return body.error.message;
      }
    } catch {
      // Response body wasn't JSON - fall through to the fallback message.
    }
  }

  return fallback;
}
