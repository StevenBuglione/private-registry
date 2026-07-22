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
  documentation?: string | undefined;
  governance?: GovernanceRecord | undefined;
  installSource?: string | undefined;
  artifactRepository?: string | undefined;
  artifactPath?: string | undefined;
  packageDigest?: string | undefined;
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
  apmId?: string | undefined;
  lifecycle?: Lifecycle | undefined;
  approval?: Approval | undefined;
  risk?: Risk | undefined;
  sort?: "relevance" | "updated" | "name" | "risk" | undefined;
  cursor?: string | undefined;
  limit?: number | undefined;
}

export interface RuntimeConfig {
  apiBaseUrl: string;
  jfrogHostname: string;
  environment: string;
  supportUrl: string;
}
