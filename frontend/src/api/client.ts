// src/api/client.ts

import ky from "ky";

function readCookie(name: string): string | undefined {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));

  return match ? decodeURIComponent(match[1]) : undefined;
}

export const api = ky.create({
  prefix: "/api/v1",
  credentials: "include",
  hooks: {
    beforeRequest: [
      ({ request }) => {
        const csrfToken = readCookie("XSRF-TOKEN");

        if (csrfToken) {
          request.headers.set("X-XSRF-TOKEN", csrfToken);
        }
      },
    ],
  },
});
