import { runtimeConfig } from "./runtime-config";
import type {
  ApmAccess,
  CatalogPage,
  CatalogQuery,
  GovernanceRecord,
  PackageDetail,
  PackageKind,
  PackageSummary,
  RegistrySession,
} from "./types";

type JsonObject = Record<string, unknown>;

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
  const response = await request<JsonObject>("/auth/session");
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
  const response = await request<JsonObject>("/auth/logout", {
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

export async function getCatalogPage(
  query: CatalogQuery,
): Promise<CatalogPage> {
  const params = new URLSearchParams();
  addParam(params, "q", query.q);
  addParam(params, "kind", query.kind);
  addParam(params, "provider", query.provider);
  addParam(params, "apm_id", query.apmId);
  addParam(params, "lifecycle", query.lifecycle);
  addParam(params, "approval", query.approval);
  addParam(params, "risk", query.risk);
  addParam(params, "sort", query.sort);
  addParam(params, "cursor", query.cursor);
  addParam(params, "limit", query.limit?.toString());

  const raw = await request<JsonObject>(
    `/catalog/packages?${params.toString()}`,
  );
  return normalizeCatalogPage(raw);
}

export async function getPackage(
  kind: PackageKind,
  namespace: string,
  name: string,
  target?: string,
  version?: string,
  apmId?: string,
): Promise<PackageDetail> {
  const parts = [kind, namespace, name, target, version]
    .filter(Boolean)
    .map((part) => encodeURIComponent(part!));
  const params = new URLSearchParams();
  addParam(params, "apm_id", apmId);
  const suffix = params.size ? `?${params.toString()}` : "";
  const raw = await request<JsonObject>(
    `/catalog/packages/${parts.join("/")}${suffix}`,
  );
  return normalizePackageDetail(raw);
}

export async function getPackageDocumentation(
  kind: PackageKind,
  namespace: string,
  name: string,
  target?: string,
  version?: string,
  apmId?: string,
): Promise<string> {
  const parts = [kind, namespace, name, target, version]
    .filter(Boolean)
    .map((part) => encodeURIComponent(part!));
  const params = new URLSearchParams();
  addParam(params, "apm_id", apmId);
  const suffix = params.size ? `?${params.toString()}` : "";
  const raw = await request<JsonObject | string>(
    `/catalog/packages/${parts.join("/")}/documentation${suffix}`,
    { headers: { Accept: "application/json, text/markdown" } },
  );
  if (typeof raw === "string") return raw;
  return firstString(raw.markdown, raw.documentation, raw.content, raw.readme);
}

export async function getPackageGovernance(
  kind: PackageKind,
  namespace: string,
  name: string,
  target?: string,
  version?: string,
  apmId?: string,
): Promise<GovernanceRecord> {
  const parts = [kind, namespace, name, target, version]
    .filter(Boolean)
    .map((part) => encodeURIComponent(part!));
  const params = new URLSearchParams();
  addParam(params, "apm_id", apmId);
  const suffix = params.size ? `?${params.toString()}` : "";
  const raw = await request<JsonObject>(
    `/catalog/packages/${parts.join("/")}/governance${suffix}`,
  );
  return normalizeGovernance(raw);
}

export function catalogEventsUrl(apmId?: string): string {
  const params = new URLSearchParams();
  addParam(params, "apm_id", apmId);
  return `${runtimeConfig().apiBaseUrl}/catalog/events${params.size ? `?${params.toString()}` : ""}`;
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
    owner: firstString(
      raw.owner,
      raw.support_owner,
      stringList(raw.owners)[0],
      raw.namespace,
      "Registry team",
    ),
    approval: enumValue(
      raw.approval,
      ["approved", "rejected", "waived"],
      "approved",
    ),
    lifecycle: enumValue(
      raw.lifecycle,
      [
        "draft",
        "candidate",
        "approved",
        "maintenance",
        "deprecated",
        "revoked",
        "archived",
      ],
      "approved",
    ),
    risk: enumValue(
      raw.risk ?? raw.riskTier ?? raw.risk_tier,
      ["low", "medium", "high", "critical"],
      "low",
    ),
    verified:
      Boolean(raw.verified ?? raw.approved) ||
      ["verified", "enterprise-verified", "official", "partner"].includes(
        firstString(raw.verification).toLowerCase(),
      ),
    updatedAt: firstString(
      raw.updatedAt,
      raw.updated_at,
      raw.last_updated,
      raw.published_at,
    ),
    apmIds: stringList(raw.apmIds ?? raw.apm_ids ?? raw.apms),
  };
}

function normalizePackageDetail(raw: JsonObject): PackageDetail {
  const envelope = objectValue(raw.data) ?? raw;
  const summary = normalizePackage(envelope);
  const rawGovernance = objectValue(envelope.governance);
  const rawVersions = firstArray(envelope.versions);
  const selectedVersion = rawVersions
    .filter(isObject)
    .find(
      (version) => firstString(version.version, version.id) === summary.version,
    );
  return {
    ...summary,
    versions: stringList(envelope.versions).length
      ? stringList(envelope.versions)
      : [summary.version].filter((value) => value !== "—"),
    documentation: optionalString(
      envelope.documentation,
      envelope.markdown,
      envelope.readme,
    ),
    governance: rawGovernance ? normalizeGovernance(rawGovernance) : undefined,
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
  };
}

function normalizeGovernance(raw: JsonObject): GovernanceRecord {
  return {
    owner: firstString(
      raw.owner,
      raw.support_owner,
      stringList(raw.owners)[0],
      "Registry team",
    ),
    support: firstString(
      raw.support,
      raw.supportLevel,
      raw.support_level,
      raw.support_channel,
      "Internal support",
    ),
    approval: enumValue(
      raw.approval,
      ["approved", "rejected", "waived"],
      "approved",
    ),
    lifecycle: enumValue(
      raw.lifecycle,
      [
        "draft",
        "candidate",
        "approved",
        "maintenance",
        "deprecated",
        "revoked",
        "archived",
      ],
      "approved",
    ),
    risk: enumValue(
      raw.risk ?? raw.riskTier ?? raw.risk_tier,
      ["low", "medium", "high", "critical"],
      "low",
    ),
    verifiedAt: optionalString(raw.verifiedAt, raw.verified_at),
    sourceRepository: optionalString(
      raw.sourceRepository,
      raw.sourceRepositoryUrl,
      raw.source_repository_url,
      raw.source_repository,
    ),
    artifactRepository: optionalString(
      raw.artifactRepository,
      raw.sourceAddress,
      raw.source_address,
      raw.artifact_repository,
    ),
    checksum: optionalString(raw.checksum, raw.sha256),
    apmIds: stringList(raw.apmIds ?? raw.apm_ids ?? raw.apms),
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

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${runtimeConfig().apiBaseUrl}${path}`, {
    credentials: "include",
    cache: "no-store",
    ...init,
    headers: {
      Accept: "application/json",
      ...init.headers,
    },
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

  if (response.status === 204) return undefined as T;
  const contentType = response.headers.get("content-type") ?? "";
  return (
    contentType.includes("json") ? await response.json() : await response.text()
  ) as T;
}

function csrfHeaders(sessionToken?: string): HeadersInit {
  const cookieToken = document.cookie
    .split(";")
    .map((value) => value.trim())
    .find((value) => value.startsWith("XSRF-TOKEN="))
    ?.split("=")
    .slice(1)
    .join("=");
  const token = sessionToken || cookieToken;
  return token ? { "X-XSRF-TOKEN": decodeURIComponent(token) } : {};
}

async function safeJson(response: Response): Promise<JsonObject | undefined> {
  try {
    return (await response.json()) as JsonObject;
  } catch {
    return undefined;
  }
}

function addParam(params: URLSearchParams, key: string, value?: string): void {
  if (value) params.set(key, value);
}

function isObject(value: unknown): value is JsonObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
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

function firstNumber(...values: unknown[]): number {
  const value = values.find(
    (candidate) =>
      typeof candidate === "number" ||
      (typeof candidate === "string" && candidate.trim() !== ""),
  );
  const result = Number(value);
  return Number.isFinite(result) ? result : 0;
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

function enumValue<T extends string>(
  value: unknown,
  values: readonly T[],
  fallback: T,
): T {
  const normalized = typeof value === "string" ? value.toLowerCase() : "";
  return values.includes(normalized as T) ? (normalized as T) : fallback;
}
