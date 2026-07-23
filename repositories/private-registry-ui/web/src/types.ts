export type PackageKind = "provider" | "module";

export interface ApmAccess {
  id: string;
  name: string;
}

export interface RegistrySession {
  subject: string;
  displayName: string;
  email: string;
  roles: string[];
  apms: ApmAccess[];
  csrfToken: string;
  loginUrl?: string | undefined;
  logoutUrl?: string | undefined;
  admin: boolean;
}

export interface HomepageSettings {
  notificationEnabled: boolean;
  notificationTitle: string;
  notificationMessage: string;
  notificationLinkLabel?: string | undefined;
  notificationLinkUrl?: string | undefined;
  featuredProviderIds: string[];
  updatedAt: string;
}

export type HomepageSettingsUpdate = Omit<HomepageSettings, "updatedAt">;

export interface PackageSummary {
  kind: PackageKind;
  namespace: string;
  name: string;
  target?: string | undefined;
  version: string;
  description: string;
  provider: string;
  verified: boolean;
  updatedAt: string;
  downloadStatistics?: DownloadStatistics | undefined;
}

export interface PackageDetail extends PackageSummary {
  versions: string[];
  symbols: PackageSymbol[];
  examples: PackageExample[];
  submodules: PackageModuleChild[];
  documentation?: string | undefined;
  installSource?: string | undefined;
  artifactRepository?: string | undefined;
  artifactPath?: string | undefined;
  packageDigest?: string | undefined;
  publishedAt?: string | undefined;
  sourceRepository?: string | undefined;
  sourceTag?: string | undefined;
  downloadStatisticsByVersion: Record<string, DownloadStatistics>;
}

export interface PackageExample {
  name: string;
  path: string;
}

export interface PackageModuleChild {
  name: string;
  path: string;
}

export interface DownloadStatistics {
  allTime: number;
  week?: number | undefined;
  month?: number | undefined;
  year?: number | undefined;
  lastDownloadedAt?: string | undefined;
  observedAt: string;
}

export interface PackageSymbol {
  kind: string;
  name: string;
  description?: string | undefined;
  path: string;
  type?: string | undefined;
  defaultValue?: unknown;
  required?: boolean | undefined;
  sensitive?: boolean | undefined;
  provider?: string | undefined;
  source?: string | undefined;
}

export interface CatalogPage {
  items: PackageSummary[];
  total: number;
  nextCursor?: string | undefined;
}

export interface CatalogQuery {
  q?: string | undefined;
  kind?: PackageKind | undefined;
  namespace?: string | undefined;
  provider?: string | undefined;
  tier?: string | undefined;
  category?: string | undefined;
  sort?: "relevance" | "updated" | "name" | "downloads" | undefined;
  cursor?: string | undefined;
  limit?: number | undefined;
}

export interface RuntimeConfig {
  apiBaseUrl: string;
  jfrogHostname: string;
  environment: string;
  supportUrl: string;
}
