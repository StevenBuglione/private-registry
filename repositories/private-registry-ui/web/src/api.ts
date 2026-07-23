import { type ZodType, z } from "zod";
import { runtimeConfig } from "./runtime-config";
import type {
  AdminDashboard,
  ApmAccess,
  AuditEvent,
  CatalogPage,
  CatalogQuery,
  CreatedSyncCredential,
  CreateSyncCredential,
  DownloadStatistics,
  HomepageSettings,
  HomepageSettingsUpdate,
  OperationalEvent,
  PackageDetail,
  PackageKind,
  PackageSummary,
  PackageSymbol,
  RegistrySession,
  SyncCredential,
} from "./types";

const wireKeys = [
  "access_contexts",
  "admin",
  "apm_entitlements",
  "apm_id",
  "apm_ids",
  "apmEntitlements",
  "apmId",
  "apmIds",
  "apms",
  "artifact_path",
  "artifact_repository",
  "artifactPath",
  "artifactRepository",
  "checksum",
  "code",
  "content",
  "csrf_token",
  "csrfToken",
  "current_version",
  "currentVersion",
  "data",
  "default",
  "default_value",
  "defaultValue",
  "description",
  "detail",
  "display_name",
  "displayName",
  "document_path",
  "documentation",
  "documentPath",
  "download_count",
  "download_statistics",
  "downloadCount",
  "downloadStatistics",
  "email",
  "entitled_apms",
  "entitledApms",
  "error",
  "error_code",
  "group_id",
  "groupId",
  "id",
  "install_source",
  "installSource",
  "is_required",
  "is_sensitive",
  "isRequired",
  "isSensitive",
  "items",
  "kind",
  "last_updated",
  "latest_version",
  "latestVersion",
  "login_url",
  "loginUrl",
  "logout_url",
  "logoutUrl",
  "last_downloaded_at",
  "lastDownloadedAt",
  "markdown",
  "message",
  "name",
  "namespace",
  "next_cursor",
  "nextCursor",
  "organization",
  "owner_namespace",
  "package_digest",
  "package_name",
  "packageDigest",
  "packages",
  "pagination",
  "path",
  "platform",
  "preferred_username",
  "provider",
  "provider_name",
  "published_at",
  "publishedAt",
  "readme",
  "redirect_uri",
  "redirect_url",
  "redirectUri",
  "redirectUrl",
  "required",
  "roles",
  "sensitive",
  "sha256",
  "source",
  "source_address",
  "source_repository",
  "source_repository_url",
  "sourceAddress",
  "sourceRepository",
  "sourceRepositoryUrl",
  "source_tag",
  "sourceTag",
  "sub",
  "subject",
  "summary",
  "symbols",
  "system",
  "target",
  "title",
  "total",
  "total_count",
  "totalElements",
  "all_time",
  "allTime",
  "week",
  "week_downloads",
  "month",
  "month_downloads",
  "year",
  "year_downloads",
  "observed_at",
  "observedAt",
  "type",
  "type_kind",
  "updated_at",
  "updatedAt",
  "user_id",
  "value",
  "value_type",
  "valueType",
  "verification",
  "verified",
  "verified_at",
  "verifiedAt",
  "version",
  "versions",
] as const;

type WireKey = (typeof wireKeys)[number];
type JsonObject = Record<string, unknown> & Partial<Record<WireKey, unknown>>;

const jsonObjectSchema = z
  .preprocess(
    (value) => (value === undefined ? {} : value),
    z.record(z.string(), z.unknown()),
  )
  .transform((value): JsonObject => value);
const documentationSchema = z.union([jsonObjectSchema, z.string()]);

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

export async function getSession(): Promise<RegistrySession> {
  const response = await request("/auth/session", jsonObjectSchema);
  const raw = objectValue(response.data) ?? response;
  const roles = stringList(raw.roles);
  const access = firstArray(
    raw.apms,
    raw.apmEntitlements,
    raw.apm_entitlements,
    raw.entitled_apms,
    raw.entitledApms,
    raw.access_contexts,
  );
  const apms = access
    .map(normalizeApm)
    .filter((value): value is ApmAccess => value !== null);

  return {
    subject: firstString(raw.subject, raw.sub, raw.user_id),
    displayName: firstString(
      raw.displayName,
      raw.display_name,
      raw.name,
      raw.email,
      "Signed-in user",
    ),
    email: firstString(raw.email, raw.preferred_username),
    roles,
    apms,
    csrfToken: firstString(raw.csrfToken, raw.csrf_token),
    loginUrl: optionalString(raw.loginUrl, raw.login_url),
    logoutUrl: optionalString(raw.logoutUrl, raw.logout_url),
    admin:
      Boolean(raw.admin) ||
      roles.includes("registry-admin") ||
      roles.includes("REGISTRY_ADMIN"),
  };
}

export async function logout(csrfToken?: string): Promise<string | undefined> {
  const response = await request("/auth/logout", jsonObjectSchema, {
    method: "POST",
    headers: csrfHeaders(csrfToken),
  });
  return optionalString(
    response.logoutUrl,
    response.logout_url,
    response.redirectUrl,
    response.redirect_url,
    response.redirectUri,
    response.redirect_uri,
  );
}

export async function getHomepageSettings(): Promise<HomepageSettings> {
  const raw = await request("/registry/homepage", jsonObjectSchema);
  return normalizeHomepageSettings(raw);
}

export async function updateHomepageSettings(
  update: HomepageSettingsUpdate,
  csrfToken?: string,
): Promise<HomepageSettings> {
  const raw = await request("/registry/homepage", jsonObjectSchema, {
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
      featured_provider_ids: update.featuredProviderIds,
      featured_module_ids: update.featuredModuleIds,
    }),
  });
  return normalizeHomepageSettings(raw);
}

export async function getCatalogPage(
  query: CatalogQuery,
): Promise<CatalogPage> {
  const params = new URLSearchParams();
  addParam(params, "q", query.q);
  addParam(params, "kind", query.kind);
  addParam(params, "namespace", query.namespace);
  addParam(params, "provider", query.provider);
  addParam(params, "tier", query.tier);
  addParam(params, "category", query.category);
  addParam(params, "sort", query.sort);
  addParam(params, "cursor", query.cursor);
  addParam(params, "page", query.page?.toString());
  addParam(params, "limit", query.limit?.toString());

  const raw = await request(
    `/catalog/packages?${params.toString()}`,
    jsonObjectSchema,
  );
  return normalizeCatalogPage(raw);
}

export async function getPackage(
  kind: PackageKind,
  namespace: string,
  name: string,
  target?: string,
  version?: string,
): Promise<PackageDetail> {
  const parts = [kind, namespace, name, target, version]
    .filter(isNonEmptyString)
    .map((part) => encodeURIComponent(part));
  const raw = await request(
    `/catalog/packages/${parts.join("/")}`,
    jsonObjectSchema,
  );
  return normalizePackageDetail(raw);
}

export async function getPackageDocumentation(
  kind: PackageKind,
  namespace: string,
  name: string,
  target?: string,
  version?: string,
  documentPath?: string,
): Promise<string> {
  const parts = [kind, namespace, name, target, version]
    .filter(isNonEmptyString)
    .map((part) => encodeURIComponent(part));
  const params = new URLSearchParams();
  addParam(params, "path", documentPath);
  const suffix = params.size ? `?${params.toString()}` : "";
  const raw = await request(
    `/catalog/packages/${parts.join("/")}/documentation${suffix}`,
    documentationSchema,
    { headers: { Accept: "application/json, text/markdown" } },
  );
  if (typeof raw === "string") return raw;
  return firstString(raw.markdown, raw.documentation, raw.content, raw.readme);
}

export function catalogEventsUrl(): string {
  return `${runtimeConfig().apiBaseUrl}/catalog/events`;
}

export async function getAdminDashboard(): Promise<AdminDashboard> {
  const raw = await request("/admin/dashboard", jsonObjectSchema);
  return normalizeAdminDashboard(raw);
}

export async function getAdminOperations(): Promise<OperationalEvent[]> {
  const raw = await request(
    "/admin/operations?limit=75",
    z.array(jsonObjectSchema),
  );
  return raw.map(normalizeOperationalEvent);
}

export async function getAuditEvents(): Promise<AuditEvent[]> {
  const raw = await request(
    "/admin/audit-events?limit=75",
    z.array(jsonObjectSchema),
  );
  return raw.map(normalizeAuditEvent);
}

export async function getSyncCredentials(): Promise<SyncCredential[]> {
  const raw = await request(
    "/admin/sync-credentials",
    z.array(jsonObjectSchema),
  );
  return raw.map(normalizeSyncCredential);
}

export async function createSyncCredential(
  value: CreateSyncCredential,
  csrfToken?: string,
): Promise<CreatedSyncCredential> {
  const raw = await request("/admin/sync-credentials", jsonObjectSchema, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...csrfHeaders(csrfToken),
    },
    body: JSON.stringify({
      name: value.name,
      scope: value.scope,
      expires_in_days: value.expiresInDays,
    }),
  });
  const credential = objectValue(raw["credential"]);
  if (credential === undefined) {
    throw new Error("Registry returned an invalid sync credential");
  }
  return {
    credential: normalizeSyncCredential(credential),
    token: firstString(raw["token"]),
  };
}

export async function revokeSyncCredential(
  id: string,
  csrfToken?: string,
): Promise<SyncCredential> {
  const raw = await request(
    `/admin/sync-credentials/${encodeURIComponent(id)}`,
    jsonObjectSchema,
    {
      method: "DELETE",
      headers: csrfHeaders(csrfToken),
    },
  );
  return normalizeSyncCredential(raw);
}

function normalizeAdminDashboard(raw: JsonObject): AdminDashboard {
  const catalog = objectValue(raw["catalog"]) ?? {};
  const queue = objectValue(raw["queue"]) ?? {};
  const ingestion = objectValue(raw["ingestion"]) ?? {};
  const reconciliation = objectValue(raw["reconciliation"]);
  const status = firstString(raw["status"]);
  return {
    generatedAt: firstString(raw["generated_at"], raw["generatedAt"]),
    status: status === "degraded" ? "degraded" : "healthy",
    workerEnabled: Boolean(raw["worker_enabled"] ?? raw["workerEnabled"]),
    dependencies: stringRecord(raw["dependencies"]),
    catalog: {
      providers: firstNumber(catalog["providers"]),
      modules: firstNumber(catalog["modules"]),
      activeVersions: firstNumber(
        catalog["active_versions"],
        catalog["activeVersions"],
      ),
      documents: firstNumber(catalog["documents"]),
      downloads: firstNumber(catalog["downloads"]),
    },
    queue: {
      queued: firstNumber(queue["queued"]),
      processing: firstNumber(queue["processing"]),
      retry: firstNumber(queue["retry"]),
      completed: firstNumber(queue["completed"]),
      deadLetter: firstNumber(queue["dead_letter"], queue["deadLetter"]),
    },
    ingestion: {
      completed: firstNumber(ingestion["completed"]),
      failed: firstNumber(ingestion["failed"]),
      quarantined: firstNumber(ingestion["quarantined"]),
      latencyP95Ms: firstNumber(
        ingestion["latency_p95_ms"],
        ingestion["latencyP95Ms"],
      ),
      lastCompletedAt: optionalString(
        ingestion["last_completed_at"],
        ingestion["lastCompletedAt"],
      ),
    },
    reconciliation:
      reconciliation === undefined
        ? undefined
        : {
            id: firstString(reconciliation["id"]),
            mode: firstString(reconciliation["mode"]),
            scope: firstString(reconciliation["scope"]),
            status: firstString(reconciliation["status"]),
            discrepancies: firstNumber(reconciliation["discrepancies"]),
            repaired: firstNumber(reconciliation["repaired"]),
            startedAt: firstString(
              reconciliation["started_at"],
              reconciliation["startedAt"],
            ),
            completedAt: optionalString(
              reconciliation["completed_at"],
              reconciliation["completedAt"],
            ),
          },
    databaseSizeBytes: firstNumber(
      raw["database_size_bytes"],
      raw["databaseSizeBytes"],
    ),
  };
}

function normalizeOperationalEvent(raw: JsonObject): OperationalEvent {
  const source = firstString(raw["source"]);
  return {
    source:
      source === "queue" || source === "reconciliation" ? source : "ingestion",
    eventId: firstString(raw["event_id"], raw["eventId"]),
    status: firstString(raw["status"]),
    title: firstString(raw["title"]),
    detail: firstString(raw["detail"]),
    repository: optionalString(raw["repository"]),
    path: optionalString(raw["path"]),
    correlationId: firstString(raw["correlation_id"], raw["correlationId"]),
    occurredAt: firstString(raw["occurred_at"], raw["occurredAt"]),
  };
}

function normalizeAuditEvent(raw: JsonObject): AuditEvent {
  return {
    id: firstString(raw["id"]),
    occurredAt: firstString(raw["occurred_at"], raw["occurredAt"]),
    actorType: firstString(raw["actor_type"], raw["actorType"]),
    actorId: firstString(raw["actor_id"], raw["actorId"]),
    action: firstString(raw["action"]),
    resourceType: firstString(raw["resource_type"], raw["resourceType"]),
    resourceId: firstString(raw["resource_id"], raw["resourceId"]),
    correlationId: firstString(raw["correlation_id"], raw["correlationId"]),
    detail: raw["detail"],
  };
}

function normalizeSyncCredential(raw: JsonObject): SyncCredential {
  const scope = firstString(raw["scope"]).toLowerCase();
  const status = firstString(raw["status"]).toLowerCase();
  return {
    id: firstString(raw["id"]),
    name: firstString(raw["name"]),
    scope: scope === "provider" || scope === "all" ? scope : "module",
    keyPrefix: firstString(raw["key_prefix"], raw["keyPrefix"]),
    createdBy: firstString(raw["created_by"], raw["createdBy"]),
    createdAt: firstString(raw["created_at"], raw["createdAt"]),
    expiresAt: firstString(raw["expires_at"], raw["expiresAt"]),
    revokedAt: optionalString(raw["revoked_at"], raw["revokedAt"]),
    revokedBy: optionalString(raw["revoked_by"], raw["revokedBy"]),
    lastUsedAt: optionalString(raw["last_used_at"], raw["lastUsedAt"]),
    useCount: firstNumber(raw["use_count"], raw["useCount"]),
    status: status === "revoked" || status === "expired" ? status : "active",
  };
}

export function normalizeCatalogPage(raw: JsonObject): CatalogPage {
  const envelope = objectValue(raw.data) ?? raw;
  const rawItems = firstArray(
    envelope.items,
    envelope.content,
    envelope.packages,
    raw.items,
  );
  const items = rawItems
    .map((entry) => (isObject(entry) ? normalizePackage(entry) : null))
    .filter((entry): entry is PackageSummary => entry !== null);
  const pagination = objectValue(envelope.pagination);
  return {
    items,
    total: firstNumber(
      envelope.total,
      envelope.total_count,
      envelope.totalElements,
      pagination?.total,
      items.length,
    ),
    nextCursor: optionalString(
      envelope.nextCursor,
      envelope.next_cursor,
      pagination?.nextCursor,
      pagination?.next_cursor,
    ),
  };
}

function normalizePackage(raw: JsonObject): PackageSummary {
  const kindValue = firstString(raw.kind, raw.type).toLowerCase();
  const downloadStatistics = aggregateVersionDownloadStatistics(
    firstArray(raw.versions),
  );
  return {
    kind: kindValue.startsWith("module") ? "module" : "provider",
    namespace: firstString(
      raw.namespace,
      raw.owner_namespace,
      raw.organization,
      "unknown",
    ),
    name: firstString(raw.name, raw.package_name, "unnamed"),
    target: optionalString(raw.target, raw.system),
    version: firstString(
      raw.version,
      raw.latestVersion,
      raw.latest_version,
      raw.currentVersion,
      raw.current_version,
      "—",
    ),
    description: firstString(
      raw.description,
      raw.summary,
      "No description is available.",
    ),
    provider: firstString(
      raw.provider,
      raw.platform,
      raw.target,
      raw.name,
      raw.namespace,
      "Other",
    ),
    verified:
      Boolean(raw.verified) ||
      ["verified", "enterprise-verified", "official", "partner"].includes(
        firstString(raw.verification).toLowerCase(),
      ),
    updatedAt: firstString(
      raw.updatedAt,
      raw.updated_at,
      raw.last_updated,
      raw.published_at,
    ),
    downloadStatistics,
  };
}

function normalizePackageDetail(raw: JsonObject): PackageDetail {
  const envelope = objectValue(raw.data) ?? raw;
  const summary = normalizePackage(envelope);
  const rawVersions = firstArray(envelope.versions);
  const selectedVersion = rawVersions
    .filter(isObject)
    .find(
      (version) => firstString(version.version, version.id) === summary.version,
    );
  const symbols = firstArray(envelope.symbols)
    .map(normalizeSymbol)
    .filter((symbol): symbol is PackageSymbol => symbol !== null);
  const rawDownloadStatistics = objectValue(
    selectedVersion?.downloadStatistics ??
      selectedVersion?.download_statistics ??
      envelope.downloadStatistics ??
      envelope.download_statistics,
  );
  const downloadStatisticsByVersion =
    normalizeVersionDownloadStatistics(rawVersions);
  const downloadStatistics = aggregateVersionDownloadStatistics(rawVersions);
  return {
    ...summary,
    versions: stringList(envelope.versions).length
      ? stringList(envelope.versions)
      : [summary.version].filter((value) => value !== "—"),
    symbols,
    examples: symbols
      .filter((symbol) => symbol.kind === "example")
      .map((symbol) => ({ name: symbol.name, path: symbol.path })),
    submodules: symbols
      .filter((symbol) => symbol.kind === "submodule")
      .map((symbol) => ({ name: symbol.name, path: symbol.path })),
    documentation: optionalString(
      envelope.documentation,
      envelope.markdown,
      envelope.readme,
    ),
    installSource: optionalString(
      envelope.installSource,
      envelope.install_source,
      envelope.sourceAddress,
      envelope.source_address,
      envelope.source,
    ),
    artifactRepository: optionalString(
      selectedVersion?.artifactRepository,
      selectedVersion?.artifact_repository,
    ),
    artifactPath: optionalString(
      selectedVersion?.artifactPath,
      selectedVersion?.artifact_path,
    ),
    packageDigest: optionalString(
      selectedVersion?.packageDigest,
      selectedVersion?.package_digest,
    ),
    publishedAt: optionalString(
      selectedVersion?.publishedAt,
      selectedVersion?.published_at,
    ),
    sourceRepository: optionalString(
      selectedVersion?.sourceRepository,
      selectedVersion?.source_repository,
    ),
    sourceTag: optionalString(
      selectedVersion?.sourceTag,
      selectedVersion?.source_tag,
    ),
    downloadStatisticsByVersion,
    downloadStatistics:
      downloadStatistics ??
      (rawDownloadStatistics === undefined
        ? undefined
        : normalizeDownloadStatistics(rawDownloadStatistics)),
  };
}

function normalizeVersionDownloadStatistics(
  rawVersions: unknown[],
): Record<string, DownloadStatistics> {
  return Object.fromEntries(
    rawVersions.filter(isObject).flatMap((version) => {
      const versionNumber = firstString(version.version, version.id);
      const rawStatistics = objectValue(
        version.downloadStatistics ?? version.download_statistics,
      );
      return versionNumber === "" || rawStatistics === undefined
        ? []
        : [[versionNumber, normalizeDownloadStatistics(rawStatistics)]];
    }),
  );
}

function aggregateVersionDownloadStatistics(
  rawVersions: unknown[],
): DownloadStatistics | undefined {
  const versions = rawVersions.filter(isObject);
  const statistics = versions
    .map((version) =>
      objectValue(version.downloadStatistics ?? version.download_statistics),
    )
    .filter((value): value is JsonObject => value !== undefined)
    .map(normalizeDownloadStatistics);
  if (statistics.length === 0 || statistics.length !== versions.length) {
    return undefined;
  }

  return {
    allTime: statistics.reduce((sum, value) => sum + value.allTime, 0),
    week: sumCompletePeriod(statistics, "week"),
    month: sumCompletePeriod(statistics, "month"),
    year: sumCompletePeriod(statistics, "year"),
    lastDownloadedAt: latestTimestamp(
      statistics.map((value) => value.lastDownloadedAt),
    ),
    observedAt:
      latestTimestamp(statistics.map((value) => value.observedAt)) ?? "",
  };
}

function sumCompletePeriod(
  statistics: DownloadStatistics[],
  period: "week" | "month" | "year",
): number | undefined {
  const values = statistics.map((value) => value[period]);
  return values.every((value): value is number => value !== undefined)
    ? values.reduce((sum, value) => sum + value, 0)
    : undefined;
}

function latestTimestamp(values: (string | undefined)[]): string | undefined {
  return values
    .filter((value): value is string => value !== undefined && value !== "")
    .sort()
    .at(-1);
}

function normalizeDownloadStatistics(raw: JsonObject): DownloadStatistics {
  return {
    allTime: firstNumber(
      raw.allTime,
      raw.all_time,
      raw.downloadCount,
      raw.download_count,
    ),
    week: optionalNumber(raw.week, raw.week_downloads),
    month: optionalNumber(raw.month, raw.month_downloads),
    year: optionalNumber(raw.year, raw.year_downloads),
    lastDownloadedAt: optionalString(
      raw.lastDownloadedAt,
      raw.last_downloaded_at,
    ),
    observedAt: firstString(raw.observedAt, raw.observed_at),
  };
}

function normalizeSymbol(value: unknown): PackageSymbol | null {
  if (!isObject(value)) return null;
  const kind = firstString(value.kind, value.type_kind).toLowerCase();
  const name = firstString(value.name, value.title);
  const path = firstString(value.path, value.documentPath, value.document_path);
  if (!kind || !name || !path) return null;
  return {
    kind,
    name,
    description: optionalString(value.description, value.summary),
    path,
    type: optionalString(value.type, value.valueType, value.value_type),
    defaultValue: firstDefined(
      value.defaultValue,
      value.default_value,
      value.default,
    ),
    required: optionalBoolean(
      value.required,
      value.isRequired,
      value.is_required,
    ),
    sensitive: optionalBoolean(
      value.sensitive,
      value.isSensitive,
      value.is_sensitive,
    ),
    provider: optionalString(value.provider, value.provider_name),
    source: optionalString(value.source, value.source_address),
  };
}

function normalizeHomepageSettings(raw: JsonObject): HomepageSettings {
  return {
    notificationEnabled: Boolean(
      raw["notificationEnabled"] ?? raw["notification_enabled"],
    ),
    notificationTitle: firstString(
      raw["notificationTitle"],
      raw["notification_title"],
    ),
    notificationMessage: firstString(
      raw["notificationMessage"],
      raw["notification_message"],
    ),
    notificationLinkLabel: optionalString(
      raw["notificationLinkLabel"],
      raw["notification_link_label"],
    ),
    notificationLinkUrl: optionalString(
      raw["notificationLinkUrl"],
      raw["notification_link_url"],
    ),
    featuredProviderIds: stringList(
      raw["featuredProviderIds"] ?? raw["featured_provider_ids"],
    ),
    featuredModuleIds: stringList(
      raw["featuredModuleIds"] ?? raw["featured_module_ids"],
    ),
    updatedAt: firstString(raw["updatedAt"], raw["updated_at"]),
  };
}

function normalizeApm(value: unknown): ApmAccess | null {
  if (typeof value === "string") return { id: value, name: value };
  if (!isObject(value)) return null;
  const id = firstString(
    value.id,
    value.apmId,
    value.apm_id,
    value.groupId,
    value.group_id,
  );
  if (!id) return null;
  return {
    id,
    name: firstString(value.name, value.displayName, value.display_name, id),
  };
}

async function request<T>(
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

  if (!response.ok) {
    const body = await safeJson(response);
    const error = objectValue(body?.error);
    throw new ApiError(
      firstString(
        error?.message,
        body?.message,
        body?.detail,
        response.statusText,
        "Registry request failed",
      ),
      response.status,
      optionalString(error?.code, body?.code, body?.error_code),
    );
  }

  if (response.status === 204) return schema.parse(undefined);
  const contentType = response.headers.get("content-type") ?? "";
  const payload: unknown = contentType.includes("json")
    ? await response.json()
    : await response.text();
  return schema.parse(payload);
}

function csrfHeaders(sessionToken?: string): Record<string, string> {
  const cookieToken = document.cookie
    .split(";")
    .map((value) => value.trim())
    .find((value) => value.startsWith("XSRF-TOKEN="))
    ?.split("=")
    .slice(1)
    .join("=");
  const token = sessionToken ?? cookieToken;
  return isNonEmptyString(token)
    ? { "X-XSRF-TOKEN": decodeURIComponent(token) }
    : {};
}

async function safeJson(response: Response): Promise<JsonObject | undefined> {
  try {
    const payload: unknown = await response.json();
    const result = jsonObjectSchema.safeParse(payload);
    return result.success ? result.data : undefined;
  } catch {
    return undefined;
  }
}

function addParam(params: URLSearchParams, key: string, value?: string): void {
  if (isNonEmptyString(value)) params.set(key, value);
}

function isObject(value: unknown): value is JsonObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isNonEmptyString(value: string | undefined): value is string {
  return typeof value === "string" && value.length > 0;
}

function objectValue(value: unknown): JsonObject | undefined {
  return isObject(value) ? value : undefined;
}

function firstArray(...values: unknown[]): unknown[] {
  return values.find(Array.isArray) ?? [];
}

function firstString(...values: unknown[]): string {
  const value = values.find(
    (candidate) => typeof candidate === "string" && candidate.trim().length > 0,
  );
  return typeof value === "string" ? value : "";
}

function optionalString(...values: unknown[]): string | undefined {
  return firstString(...values) || undefined;
}

function firstDefined(...values: unknown[]): unknown {
  return values.find((value) => value !== undefined);
}

function optionalBoolean(...values: unknown[]): boolean | undefined {
  const value = firstDefined(...values);
  if (typeof value === "boolean") return value;
  if (typeof value === "string" && /^(true|false)$/i.test(value))
    return value.toLowerCase() === "true";
  return undefined;
}

function firstNumber(...values: unknown[]): number {
  const value = values.find(
    (candidate) =>
      typeof candidate === "number" ||
      (typeof candidate === "string" && candidate.trim() !== ""),
  );
  const result = Number(value);
  return Number.isFinite(result) ? result : 0;
}

function optionalNumber(...values: unknown[]): number | undefined {
  const value = values.find(
    (candidate) =>
      candidate !== null &&
      candidate !== undefined &&
      (typeof candidate === "number" ||
        (typeof candidate === "string" && candidate.trim() !== "")),
  );
  if (value === undefined) return undefined;
  const result = Number(value);
  return Number.isFinite(result) ? result : undefined;
}

function stringList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value
    .map((entry) =>
      typeof entry === "string"
        ? entry
        : isObject(entry)
          ? firstString(entry.id, entry.value, entry.version)
          : "",
    )
    .filter(Boolean);
}

function stringRecord(value: unknown): Record<string, string> {
  if (!isObject(value)) return {};
  return Object.fromEntries(
    Object.entries(value).filter(
      (entry): entry is [string, string] => typeof entry[1] === "string",
    ),
  );
}
