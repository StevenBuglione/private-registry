import { type ZodType, z } from "zod";
import { runtimeConfig } from "../runtime-config";

const nestedErrorSchema = z
  .object({
    error: z
      .object({
        code: z.string().optional(),
        message: z.string().optional(),
      })
      .strict(),
  })
  .strict();

const problemDetailSchema = z
  .object({
    type: z.string().optional(),
    title: z.string().optional(),
    status: z.number().optional(),
    detail: z.string().optional(),
    instance: z.string().optional(),
    code: z.string().optional(),
    error_code: z.string().optional(),
    message: z.string().optional(),
  })
  .strict();

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export async function request<T>(
  path: string,
  schema: ZodType<T>,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  if (!headers.has("Accept")) headers.set("Accept", "application/json");
  const response = await fetch(`${runtimeConfig().apiBaseUrl}${path}`, {
    credentials: "include",
    cache: "no-store",
    ...init,
    headers,
  });

  if (!response.ok) throw await apiError(response);
  if (response.status === 204) return schema.parse(undefined);

  const contentType = response.headers.get("content-type") ?? "";
  const payload: unknown = contentType.includes("json")
    ? await response.json()
    : await response.text();
  return schema.parse(payload);
}

export function csrfHeaders(sessionToken?: string): Record<string, string> {
  const cookieToken = document.cookie
    .split(";")
    .map((value) => value.trim())
    .find((value) => value.startsWith("XSRF-TOKEN="))
    ?.split("=")
    .slice(1)
    .join("=");
  const token = sessionToken ?? cookieToken;
  return token === undefined || token.length === 0
    ? {}
    : { "X-XSRF-TOKEN": decodeURIComponent(token) };
}

export function addQueryParameter(
  parameters: URLSearchParams,
  name: string,
  value?: string,
): void {
  if (value !== undefined && value.length > 0) parameters.set(name, value);
}

export function encodedPath(parts: Array<string | undefined>): string {
  return parts
    .filter((part): part is string => part !== undefined && part.length > 0)
    .map((part) => encodeURIComponent(part))
    .join("/");
}

async function apiError(response: Response): Promise<ApiError> {
  const fallback = response.statusText || "Registry request failed";
  try {
    const payload: unknown = await response.json();
    const nested = nestedErrorSchema.safeParse(payload);
    if (nested.success) {
      return new ApiError(
        nested.data.error.message ?? fallback,
        response.status,
        nested.data.error.code,
      );
    }
    const problem = problemDetailSchema.safeParse(payload);
    if (problem.success) {
      return new ApiError(
        problem.data.message ??
          problem.data.detail ??
          problem.data.title ??
          fallback,
        response.status,
        problem.data.code ?? problem.data.error_code,
      );
    }
  } catch {
    // A non-JSON error body has no safe structured details to expose.
  }
  return new ApiError(fallback, response.status);
}
