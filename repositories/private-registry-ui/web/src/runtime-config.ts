import { z } from "zod";
import type { RuntimeConfig } from "./types";

const defaults: RuntimeConfig = {
  apiBaseUrl: "/api/v1",
  jfrogHostname: "",
  environment: "unknown",
  supportUrl: "",
};

let current = defaults;

export async function loadRuntimeConfig(): Promise<RuntimeConfig> {
  try {
    const response = await fetch("/config/runtime.json", {
      cache: "no-store",
      credentials: "same-origin",
    });
    if (!response.ok) return current;

    const payload: unknown = await response.json();
    const parsed = z.record(z.string(), z.unknown()).safeParse(payload);
    if (!parsed.success) return current;
    const raw = parsed.data;
    current = {
      apiBaseUrl: cleanBase(raw["apiBaseUrl"], defaults.apiBaseUrl),
      jfrogHostname: stringValue(raw["jfrogHostname"]),
      environment: stringValue(raw["environment"]) || defaults.environment,
      supportUrl: stringValue(raw["supportUrl"]),
    };
  } catch {
    // Local Vite development intentionally runs without a generated config file.
  }
  return current;
}

export function runtimeConfig(): RuntimeConfig {
  return current;
}

function cleanBase(value: unknown, fallback: string): string {
  const result = stringValue(value) || fallback;
  return result.endsWith("/") ? result.slice(0, -1) : result;
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value : "";
}
