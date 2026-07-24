import { z } from "zod";
import type { HomepageSettings, HomepageSettingsUpdate } from "../types";
import { csrfHeaders, request } from "./client";

const homepageSettingsSchema = z
  .object({
    notification_enabled: z.boolean(),
    notification_title: z.string(),
    notification_message: z.string(),
    notification_link_label: z.string().optional(),
    notification_link_url: z.string().optional(),
    featured_providers_enabled: z.boolean(),
    featured_modules_enabled: z.boolean(),
    featured_provider_ids: z.array(z.string()),
    featured_module_ids: z.array(z.string()),
    updated_at: z.string().min(1),
  })
  .strict()
  .transform(
    (wire): HomepageSettings => ({
      notificationEnabled: wire.notification_enabled,
      notificationTitle: wire.notification_title,
      notificationMessage: wire.notification_message,
      notificationLinkLabel: wire.notification_link_label,
      notificationLinkUrl: wire.notification_link_url,
      featuredProvidersEnabled: wire.featured_providers_enabled,
      featuredModulesEnabled: wire.featured_modules_enabled,
      featuredProviderIds: wire.featured_provider_ids,
      featuredModuleIds: wire.featured_module_ids,
      updatedAt: wire.updated_at,
    }),
  );

export async function getHomepageSettings(): Promise<HomepageSettings> {
  return request("/registry/homepage", homepageSettingsSchema);
}

export async function updateHomepageSettings(
  update: HomepageSettingsUpdate,
  csrfToken?: string,
): Promise<HomepageSettings> {
  return request("/registry/homepage", homepageSettingsSchema, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      ...csrfHeaders(csrfToken),
    },
    body: JSON.stringify({
      notification_enabled: update.notificationEnabled,
      notification_title: update.notificationTitle,
      notification_message: update.notificationMessage,
      notification_link_label: update.notificationLinkLabel,
      notification_link_url: update.notificationLinkUrl,
      featured_providers_enabled: update.featuredProvidersEnabled,
      featured_modules_enabled: update.featuredModulesEnabled,
      featured_provider_ids: update.featuredProviderIds,
      featured_module_ids: update.featuredModuleIds,
    }),
  });
}
