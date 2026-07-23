import { z } from "zod";
import { csrfHeaders, request } from "./client";

export async function recordPageView(
  path: string,
  csrfToken?: string,
): Promise<void> {
  await request("/analytics/page-views", z.undefined(), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...csrfHeaders(csrfToken),
    },
    body: JSON.stringify({ path }),
  });
}
