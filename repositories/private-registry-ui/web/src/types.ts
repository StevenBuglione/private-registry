export type PackageKind = "provider" | "module";
export type Approval = "approved" | "rejected" | "waived";
export type Lifecycle =
  | "draft"
  | "candidate"
  | "approved"
  | "maintenance"
  | "deprecated"
  | "revoked"
  | "archived";
export type Risk = "low" | "medium" | "high" | "critical";

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
  owner: string;
  approval: Approval;
  lifecycle: Lifecycle;
  risk: Risk;
  verified: boolean;
  updatedAt: string;
  apmIds: string[];
  downloadStatistics?: DownloadStatistics | undefined;
}

export interface GovernanceRecord {
  owner: string;
  support: string;
  approval: Approval;
  lifecycle: Lifecycle;
  risk: Risk;
  verifiedAt?: string | undefined;
  sourceRepository?: string | undefined;
  artifactRepository?: string | undefined;
  checksum?: string | undefined;
  apmIds: string[];
}

export interface PackageDetail extends PackageSummary {
  versions: string[];
  symbols: PackageSymbol[];
  examples: PackageExample[];
  documentation?: string | undefined;
  governance?: GovernanceRecord | undefined;
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
  provider?: string | undefined;
  tier?: string | undefined;
  category?: string | undefined;
  apmId?: string | undefined;
  lifecycle?: Lifecycle | undefined;
  approval?: Approval | undefined;
  risk?: Risk | undefined;
  sort?: "relevance" | "updated" | "name" | "risk" | "downloads" | undefined;
  cursor?: string | undefined;
  limit?: number | undefined;
}

export interface RuntimeConfig {
  apiBaseUrl: string;
  jfrogHostname: string;
  environment: string;
  supportUrl: string;
}
