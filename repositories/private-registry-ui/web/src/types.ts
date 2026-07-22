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
  loginUrl?: string;
  logoutUrl?: string;
  admin: boolean;
}

export interface PackageSummary {
  kind: PackageKind;
  namespace: string;
  name: string;
  target?: string;
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
  verifiedAt?: string;
  sourceRepository?: string;
  artifactRepository?: string;
  checksum?: string;
  apmIds: string[];
}

export interface PackageDetail extends PackageSummary {
  versions: string[];
  documentation?: string;
  governance?: GovernanceRecord;
  installSource?: string;
  artifactRepository?: string;
  artifactPath?: string;
  packageDigest?: string;
}

export interface CatalogPage {
  items: PackageSummary[];
  total: number;
  nextCursor?: string;
}

export interface CatalogQuery {
  q?: string;
  kind?: PackageKind;
  provider?: string;
  apmId?: string;
  lifecycle?: Lifecycle;
  approval?: Approval;
  risk?: Risk;
  sort?: "relevance" | "updated" | "name" | "risk";
  cursor?: string;
  limit?: number;
}

export interface RuntimeConfig {
  apiBaseUrl: string;
  jfrogHostname: string;
  environment: string;
  supportUrl: string;
}
