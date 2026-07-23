export type PackageKind = "provider" | "module";

interface ApmAccess {
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
  featuredModuleIds: string[];
  updatedAt: string;
}

export type HomepageSettingsUpdate = Omit<HomepageSettings, "updatedAt">;

export interface PackageSummary {
  kind: PackageKind;
  registryTier: "official" | "partner" | "partner-premier" | "community";
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
  page?: number | undefined;
  limit?: number | undefined;
}

export interface RuntimeConfig {
  apiBaseUrl: string;
  jfrogHostname: string;
  environment: string;
  supportUrl: string;
}

export interface AdminDashboard {
  generatedAt: string;
  status: "healthy" | "degraded";
  workerEnabled: boolean;
  dependencies: Record<string, string>;
  catalog: {
    providers: number;
    modules: number;
    activeVersions: number;
    documents: number;
    downloads: number;
  };
  queue: {
    queued: number;
    processing: number;
    retry: number;
    completed: number;
    deadLetter: number;
  };
  ingestion: {
    completed: number;
    failed: number;
    quarantined: number;
    latencyP95Ms: number;
    lastCompletedAt?: string | undefined;
  };
  reconciliation?:
    | {
        id: string;
        mode: string;
        scope: string;
        status: string;
        discrepancies: number;
        repaired: number;
        startedAt: string;
        completedAt?: string | undefined;
      }
    | undefined;
  databaseSizeBytes: number;
}

export interface TrafficReport {
  generatedAt: string;
  days: number;
  summary: {
    pageViews: number;
    uniqueVisitors: number;
    pageViewsToday: number;
    visitorsToday: number;
  };
  daily: DailyTraffic[];
  topRoutes: RouteTraffic[];
  visitors: VisitorTraffic[];
  recentAccess: RecentAccess[];
}

export interface DailyTraffic {
  day: string;
  pageViews: number;
  uniqueVisitors: number;
}

interface RouteTraffic {
  path: string;
  pageViews: number;
  uniqueVisitors: number;
  lastViewedAt: string;
}

interface VisitorTraffic {
  subject: string;
  displayName: string;
  email?: string | undefined;
  pageViews: number;
  firstSeenAt: string;
  lastSeenAt: string;
  lastPath: string;
}

interface RecentAccess {
  subject: string;
  displayName: string;
  email?: string | undefined;
  path: string;
  occurredAt: string;
}

export interface OperationalEvent {
  source: "ingestion" | "queue" | "reconciliation";
  eventId: string;
  status: string;
  title: string;
  detail: string;
  repository?: string | undefined;
  path?: string | undefined;
  correlationId: string;
  occurredAt: string;
}

export interface AuditEvent {
  id: string;
  occurredAt: string;
  actorType: string;
  actorId: string;
  action: string;
  resourceType: string;
  resourceId: string;
  correlationId: string;
  detail: unknown;
}

export type SyncCredentialScope = "module" | "provider" | "all";
type SyncCredentialStatus = "active" | "expired" | "revoked";

export interface SyncCredential {
  id: string;
  name: string;
  scope: SyncCredentialScope;
  keyPrefix: string;
  createdBy: string;
  createdAt: string;
  expiresAt: string;
  revokedAt?: string | undefined;
  revokedBy?: string | undefined;
  lastUsedAt?: string | undefined;
  useCount: number;
  status: SyncCredentialStatus;
}

export interface CreatedSyncCredential {
  credential: SyncCredential;
  token: string;
}

export interface CreateSyncCredential {
  name: string;
  scope: SyncCredentialScope;
  expiresInDays: number;
}
