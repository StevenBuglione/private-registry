import { z } from "zod";
import { runtimeConfig } from "../runtime-config";
import type {
  CatalogPage,
  CatalogQuery,
  DownloadStatistics,
  PackageDetail,
  PackageKind,
  PackageSummary,
  PackageSymbol,
} from "../types";
import { addQueryParameter, encodedPath, request } from "./client";

const downloadStatisticsSchema = z
  .object({
    all_time: z.number().nonnegative(),
    week: z.number().nonnegative().optional(),
    month: z.number().nonnegative().optional(),
    year: z.number().nonnegative().optional(),
    last_downloaded_at: z.string().min(1).optional(),
    observed_at: z.string().min(1),
  })
  .strict();

const packageVersionSchema = z
  .object({
    version: z.string().min(1),
    published_at: z.string().min(1),
    package_digest: z.string().min(1),
    documentation_digest: z.string().min(1),
    documentation_root: z.string().min(1),
    artifact_repository: z.string().min(1),
    artifact_path: z.string().min(1),
    source_repository: z.string().min(1),
    source_commit: z.string().min(1),
    source_tag: z.string().min(1),
    prerelease: z.boolean(),
    deprecated: z.boolean(),
    revoked: z.boolean(),
    download_statistics: downloadStatisticsSchema.optional(),
  })
  .strict();

const packageSymbolSchema = z
  .object({
    kind: z.string().min(1),
    name: z.string().min(1),
    description: z.string().optional(),
    path: z.string().min(1),
    type: z.string().optional(),
    default_value: z.string().optional(),
    required: z.boolean(),
    sensitive: z.boolean(),
  })
  .strict();

const catalogPackageSchema = z
  .object({
    id: z.string().min(1),
    kind: z.enum(["provider", "module"]),
    namespace: z.string().min(1),
    name: z.string().min(1),
    target: z.string(),
    title: z.string().min(1),
    description: z.string(),
    latest_version: z.string().min(1),
    verification: z.string(),
    registry_tier: z.enum([
      "official",
      "partner",
      "partner-premier",
      "community",
    ]),
    source_address: z.string().min(1),
    updated_at: z.string().min(1),
    versions: z.array(packageVersionSchema),
    symbols: z.array(packageSymbolSchema),
  })
  .strict();

const catalogPageSchema = z
  .object({
    items: z.array(catalogPackageSchema),
    next_cursor: z.string().min(1).optional(),
    total: z.number().int().nonnegative(),
  })
  .strict();

type WireCatalogPackage = z.infer<typeof catalogPackageSchema>;
type WirePackageVersion = z.infer<typeof packageVersionSchema>;

export async function getCatalogPage(
  query: CatalogQuery,
): Promise<CatalogPage> {
  const parameters = new URLSearchParams();
  addQueryParameter(parameters, "q", query.q);
  addQueryParameter(parameters, "kind", query.kind);
  addQueryParameter(parameters, "namespace", query.namespace);
  addQueryParameter(parameters, "provider", query.provider);
  addQueryParameter(parameters, "tier", query.tier);
  addQueryParameter(parameters, "category", query.category);
  addQueryParameter(parameters, "sort", query.sort);
  addQueryParameter(parameters, "cursor", query.cursor);
  addQueryParameter(parameters, "page", query.page?.toString());
  addQueryParameter(parameters, "limit", query.limit?.toString());

  const suffix = parameters.size > 0 ? `?${parameters.toString()}` : "";
  const wire = await request(`/catalog/packages${suffix}`, catalogPageSchema);
  return catalogPageFromWire(wire);
}

export async function getPackage(
  kind: PackageKind,
  namespace: string,
  name: string,
  target?: string,
  version?: string,
): Promise<PackageDetail> {
  const path = encodedPath([kind, namespace, name, target, version]);
  const wire = await request(`/catalog/packages/${path}`, catalogPackageSchema);
  return packageDetailFromWire(wire, version);
}

export async function getPackageDocumentation(
  kind: PackageKind,
  namespace: string,
  name: string,
  target?: string,
  version?: string,
  documentPath?: string,
): Promise<string> {
  const path = encodedPath([kind, namespace, name, target, version]);
  const parameters = new URLSearchParams();
  addQueryParameter(parameters, "path", documentPath);
  const suffix = parameters.size > 0 ? `?${parameters.toString()}` : "";
  return request(
    `/catalog/packages/${path}/documentation${suffix}`,
    z.string(),
    { headers: { Accept: "text/markdown, text/plain" } },
  );
}

export function catalogEventsUrl(): string {
  return `${runtimeConfig().apiBaseUrl}/catalog/events`;
}

export function normalizeCatalogPage(value: unknown): CatalogPage {
  return catalogPageFromWire(catalogPageSchema.parse(value));
}

export function packageDetailFromWire(
  wire: WireCatalogPackage,
  selectedVersion?: string,
): PackageDetail {
  const summary = packageSummaryFromWire(wire);
  const selected = findVersion(wire, selectedVersion ?? wire.latest_version);
  const symbols = wire.symbols.map(symbolFromWire);
  return {
    ...summary,
    versions: wire.versions.map((version) => version.version),
    symbols,
    examples: symbols
      .filter((symbol) => symbol.kind === "example")
      .map(({ name, path }) => ({ name, path })),
    submodules: symbols
      .filter((symbol) => symbol.kind === "submodule")
      .map(({ name, path }) => ({ name, path })),
    installSource: wire.source_address,
    artifactRepository: selected?.artifact_repository,
    artifactPath: selected?.artifact_path,
    packageDigest: selected?.package_digest,
    publishedAt: selected?.published_at,
    sourceRepository: selected?.source_repository,
    sourceTag: selected?.source_tag,
    downloadStatisticsByVersion: Object.fromEntries(
      wire.versions.flatMap((version) =>
        version.download_statistics === undefined
          ? []
          : [
              [
                version.version,
                downloadStatisticsFromWire(version.download_statistics),
              ],
            ],
      ),
    ),
  };
}

function catalogPageFromWire(
  wire: z.infer<typeof catalogPageSchema>,
): CatalogPage {
  return {
    items: wire.items.map(packageSummaryFromWire),
    total: wire.total,
    nextCursor: wire.next_cursor,
  };
}

function packageSummaryFromWire(wire: WireCatalogPackage): PackageSummary {
  return {
    kind: wire.kind,
    registryTier: wire.registry_tier,
    namespace: wire.namespace,
    name: wire.name,
    target: wire.target || undefined,
    version: wire.latest_version,
    description: wire.description,
    provider: wire.kind === "module" ? wire.target : wire.name,
    verified: [
      "verified",
      "enterprise-verified",
      "official",
      "partner",
    ].includes(wire.verification.toLowerCase()),
    updatedAt: wire.updated_at,
    downloadStatistics: aggregateDownloadStatistics(wire.versions),
  };
}

function symbolFromWire(
  wire: z.infer<typeof packageSymbolSchema>,
): PackageSymbol {
  return {
    kind: wire.kind.toLowerCase(),
    name: wire.name,
    description: wire.description,
    path: wire.path,
    type: wire.type,
    defaultValue: wire.default_value,
    required: wire.required,
    sensitive: wire.sensitive,
  };
}

function findVersion(
  wire: WireCatalogPackage,
  version: string,
): WirePackageVersion | undefined {
  return wire.versions.find((candidate) => candidate.version === version);
}

function aggregateDownloadStatistics(
  versions: WirePackageVersion[],
): DownloadStatistics | undefined {
  if (
    versions.length === 0 ||
    versions.some((version) => version.download_statistics === undefined)
  ) {
    return undefined;
  }
  const statistics = versions.flatMap((version) =>
    version.download_statistics === undefined
      ? []
      : [downloadStatisticsFromWire(version.download_statistics)],
  );
  return {
    allTime: statistics.reduce((total, value) => total + value.allTime, 0),
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

function downloadStatisticsFromWire(
  wire: z.infer<typeof downloadStatisticsSchema>,
): DownloadStatistics {
  return {
    allTime: wire.all_time,
    week: wire.week,
    month: wire.month,
    year: wire.year,
    lastDownloadedAt: wire.last_downloaded_at,
    observedAt: wire.observed_at,
  };
}

function sumCompletePeriod(
  values: DownloadStatistics[],
  period: "week" | "month" | "year",
): number | undefined {
  const periodValues = values.map((value) => value[period]);
  const completeValues = periodValues.filter(
    (value): value is number => value !== undefined,
  );
  return completeValues.length === periodValues.length
    ? completeValues.reduce((total, value) => total + value, 0)
    : undefined;
}

function latestTimestamp(
  values: Array<string | undefined>,
): string | undefined {
  return values
    .filter((value): value is string => value !== undefined)
    .sort()
    .at(-1);
}
