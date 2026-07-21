export type RegistryRuntimeConfig = {
  dataApiUrl: string;
  enterpriseApiUrl: string;
  jfrogHostname: string;
  environment: string;
  supportUrl: string;
  features: {
    providers: boolean;
    modules: boolean;
    securityTab: boolean;
    auditTab: boolean;
  };
};

const DEFAULT_CONFIG: RegistryRuntimeConfig = {
  dataApiUrl: "/registry/docs/",
  enterpriseApiUrl: "/api/v1/enterprise",
  jfrogHostname: "",
  environment: "development",
  supportUrl: "",
  features: {
    providers: true,
    modules: true,
    securityTab: true,
    auditTab: false,
  },
};

let cachedConfig: RegistryRuntimeConfig | undefined;

export async function loadRegistryRuntimeConfig(): Promise<RegistryRuntimeConfig> {
  if (cachedConfig) return cachedConfig;

  const response = await fetch("/config/runtime.json", {
    cache: "no-store",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw new Error(
      `Unable to load runtime configuration (${response.status})`,
    );
  }

  const candidate = (await response.json()) as Partial<RegistryRuntimeConfig>;
  cachedConfig = {
    ...DEFAULT_CONFIG,
    ...candidate,
    features: { ...DEFAULT_CONFIG.features, ...(candidate.features ?? {}) },
  };
  return cachedConfig;
}

export function getLoadedRegistryRuntimeConfig(): RegistryRuntimeConfig {
  if (!cachedConfig) {
    throw new Error(
      "Runtime configuration must be loaded before application bootstrap",
    );
  }
  return cachedConfig;
}
