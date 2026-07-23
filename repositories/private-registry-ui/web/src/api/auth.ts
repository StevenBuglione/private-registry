import { z } from "zod";
import type { RegistrySession } from "../types";
import { csrfHeaders, request } from "./client";

const entitlementSchema = z
  .object({
    apm_id: z.string().min(1),
    display_name: z.string().min(1),
  })
  .strict();

const sessionSchema = z
  .object({
    subject: z.string().min(1),
    display_name: z.string().min(1),
    email: z.string().min(1).optional(),
    roles: z.array(z.string()),
    apm_entitlements: z.array(entitlementSchema),
    csrf_token: z.string().min(1),
  })
  .strict()
  .transform(
    (wire): RegistrySession => ({
      subject: wire.subject,
      displayName: wire.display_name,
      email: wire.email ?? "",
      roles: wire.roles,
      apms: wire.apm_entitlements.map((entitlement) => ({
        id: entitlement.apm_id,
        name: entitlement.display_name,
      })),
      csrfToken: wire.csrf_token,
      admin:
        wire.roles.includes("registry-admin") ||
        wire.roles.includes("REGISTRY_ADMIN"),
    }),
  );

const logoutSchema = z.object({ redirect_uri: z.string().min(1) }).strict();

export async function getSession(): Promise<RegistrySession> {
  return request("/auth/session", sessionSchema);
}

export async function logout(csrfToken?: string): Promise<string> {
  const response = await request("/auth/logout", logoutSchema, {
    method: "POST",
    headers: csrfHeaders(csrfToken),
  });
  return response.redirect_uri;
}
