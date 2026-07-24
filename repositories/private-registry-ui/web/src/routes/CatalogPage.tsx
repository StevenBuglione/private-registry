import {
  CaretLeftIcon,
  CaretRightIcon,
  HandshakeIcon,
  ListIcon,
  SealCheckIcon,
} from "@phosphor-icons/react";
import { type ReactNode, useMemo, useState } from "react";
import { NavLink, useParams, useSearchParams } from "react-router";
import { type FilterState, Filters } from "../components/Filters";
import {
  moduleProviderOptions,
  providerCategoryOptions,
  providerTierOptions,
} from "../components/filter-options";
import { PackageCard } from "../components/PackageCard";
import { StatePanel } from "../components/StatePanel";
import { useCatalogPage } from "../hooks/catalog";
import type { PackageKind, PackageSummary } from "../types";
import { hasText } from "../utils";

const providerTierValues = providerTierOptions.map((option) => option.value);
const providerCategoryValues = providerCategoryOptions.map(([value]) => value);
const moduleProviderValues = moduleProviderOptions.map(([value]) => value);

export function CatalogPage({ kind }: { kind?: PackageKind }) {
  const params = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const [mobileFilters, setMobileFilters] = useState(false);
  const filters: FilterState = useMemo(
    () => ({
      kind:
        kind ??
        enumParam<PackageKind>(searchParams.get("kind"), [
          "provider",
          "module",
        ]),
      provider: csvParam(searchParams.get("provider"), moduleProviderValues),
      tier: csvParam(searchParams.get("tier"), [...providerTierValues, "none"]),
      category: csvParam(searchParams.get("category"), providerCategoryValues),
    }),
    [kind, searchParams],
  );
  const effectiveKind = kind ?? filters.kind;
  const namespace =
    params["namespace"] ?? searchParams.get("namespace") ?? undefined;
  const q = searchParams.get("q") ?? "";
  const sort = q ? "relevance" : "updated";
  const pageSize = 9;
  const pageNumber = positiveIntegerParam(searchParams.get("page"));
  const result = useCatalogPage({
    q,
    kind: effectiveKind,
    namespace,
    provider: filters.provider,
    tier: filters.tier,
    category: filters.category,
    sort,
    page: pageNumber,
    limit: pageSize,
  });

  const updateParam = (key: string, value?: string) => {
    const next = new URLSearchParams(searchParams);
    if (hasText(value)) next.set(key, value);
    else next.delete(key);
    next.delete("page");
    setSearchParams(next, { replace: true });
  };
  const updateFilter = <K extends keyof FilterState>(
    key: K,
    value?: FilterState[K],
  ) => {
    updateParam(key, value);
  };
  const clear = () => {
    const next = new URLSearchParams();
    if (q) next.set("q", q);
    if (kind === undefined && effectiveKind !== undefined) {
      next.set("kind", effectiveKind);
    }
    setSearchParams(next, { replace: true });
  };

  const title =
    hasText(namespace) && effectiveKind === undefined
      ? namespace
      : effectiveKind === "provider"
        ? "Providers"
        : effectiveKind === "module"
          ? "Modules"
          : q
            ? `Results for “${q}”`
            : "Browse";
  const description =
    hasText(namespace) && effectiveKind === undefined
      ? `Providers and modules published by ${namespace}.`
      : effectiveKind === "provider"
        ? "Providers are a logical abstraction of an upstream API. They are responsible for understanding API interactions and exposing resources."
        : effectiveKind === "module"
          ? "Modules are self-contained packages of Terraform configurations that are managed as a group."
          : "Browse providers and modules available through your Registry.";

  const providerItems =
    result.data?.items.filter((item) => item.kind === "provider") ?? [];
  const moduleItems =
    result.data?.items.filter((item) => item.kind === "module") ?? [];

  return (
    <div className="catalog-page">
      <nav
        className="artifact-tabs source-container"
        aria-label="Artifact types"
      >
        <NavLink to={browseTabHref("provider", q, namespace)}>
          Providers
        </NavLink>
        <NavLink to={browseTabHref("module", q, namespace)}>Modules</NavLink>
      </nav>
      <button
        className="mobile-filter-button"
        type="button"
        onClick={() => {
          setMobileFilters((value) => !value);
        }}
        aria-expanded={mobileFilters}
        aria-controls="catalog-filters"
      >
        <ListIcon size={18} /> Filter {filterNoun(effectiveKind)}
      </button>
      <div className="catalog-layout">
        <div id="catalog-filters" className="catalog-filter-region">
          <Filters
            value={filters}
            kindLocked={effectiveKind}
            mobileOpen={mobileFilters}
            onChange={updateFilter}
            onClear={clear}
          />
        </div>
        <div className="catalog-content">
          <header className="catalog-heading">
            <h1>{title}</h1>
            <p>{description}</p>
          </header>
          {result.isPending ? (
            <div
              className="catalog-loading skeleton"
              aria-label="Loading catalog"
            />
          ) : null}
          {result.isError ? (
            <StatePanel kind="api-error" action={() => void result.refetch()} />
          ) : null}
          {result.data !== undefined && result.data.items.length === 0 ? (
            <StatePanel kind="empty" />
          ) : null}
          {providerItems.length > 0 ? (
            <ProviderResults
              items={providerItems}
              showFeatured={!q && !hasText(namespace)}
            />
          ) : null}
          {moduleItems.length > 0 ? (
            <ModuleResults items={moduleItems} />
          ) : null}
          {(result.data?.items.length ?? 0) > 0 ? (
            <CatalogPagination
              pageNumber={pageNumber}
              pageSize={pageSize}
              itemCount={result.data?.items.length ?? 0}
              total={result.data?.total ?? 0}
              onPageChange={(page) => {
                const next = new URLSearchParams(searchParams);
                if (page === 1) next.delete("page");
                else next.set("page", String(page));
                setSearchParams(next);
              }}
            />
          ) : null}
        </div>
      </div>
    </div>
  );
}

function ProviderResults({
  items,
  showFeatured,
}: {
  items: PackageSummary[];
  showFeatured: boolean;
}) {
  const official = items.filter((item) => providerTier(item) === "official");
  const partner = items.filter((item) => providerTier(item) === "partner");
  const community = items.filter((item) => providerTier(item) === "community");
  return (
    <>
      {showFeatured ? (
        <CatalogSection title="Featured Providers" items={items.slice(0, 6)} />
      ) : null}
      <CatalogSection
        title="Official Providers"
        items={official}
        badge={<SealCheckIcon size={14} />}
      />
      <CatalogSection
        title="Partner Providers"
        items={partner}
        badge={<HandshakeIcon size={14} />}
        badgeTone="partner"
      />
      <CatalogSection title="Community Providers" items={community} />
    </>
  );
}

function ModuleResults({ items }: { items: PackageSummary[] }) {
  return (
    <div className="source-card-grid modules catalog-module-results">
      {items.map((item) => (
        <PackageCard
          key={`${item.namespace}-${item.name}-${item.target ?? "general"}`}
          item={item}
        />
      ))}
    </div>
  );
}

function CatalogSection({
  title,
  items,
  badge,
  badgeTone,
}: {
  title: string;
  items: PackageSummary[];
  badge?: ReactNode;
  badgeTone?: "partner" | undefined;
}) {
  if (items.length === 0) return null;
  return (
    <section className="catalog-result-section">
      <h2>
        {title}
        {badge === undefined ? null : (
          <span className={`catalog-tier-badge ${badgeTone ?? "official"}`}>
            {badge}
            {badgeTone === "partner" ? "Partner" : "Official"}
          </span>
        )}
      </h2>
      <div className="source-card-grid providers">
        {items.map((item) => (
          <PackageCard
            key={`${item.namespace}-${item.name}`}
            item={item}
            compactProvider={title !== "Featured Providers"}
          />
        ))}
      </div>
    </section>
  );
}

function CatalogPagination({
  pageNumber,
  pageSize,
  itemCount,
  total,
  onPageChange,
}: {
  pageNumber: number;
  pageSize: number;
  itemCount: number;
  total: number;
  onPageChange: (page: number) => void;
}) {
  const first = (pageNumber - 1) * pageSize + 1;
  const last = first + itemCount - 1;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const pages = paginationItems(pageNumber, totalPages);
  return (
    <div className="catalog-pagination-row">
      <span className="catalog-result-count">
        {first}–{last} of {total}
      </span>
      <nav className="pagination" aria-label="Catalog pages">
        <button
          type="button"
          disabled={pageNumber <= 1}
          onClick={() => {
            onPageChange(pageNumber - 1);
          }}
          aria-label="Previous page"
        >
          <CaretLeftIcon size={16} />
        </button>
        <ol>
          {pages.map((page, index) =>
            page === "ellipsis" ? (
              <li
                className="pagination-ellipsis"
                aria-hidden="true"
                key={`ellipsis-${String(index)}`}
              >
                …
              </li>
            ) : (
              <li key={page}>
                <button
                  type="button"
                  className={page === pageNumber ? "active" : ""}
                  aria-current={page === pageNumber ? "page" : undefined}
                  aria-label={`Page ${String(page)}`}
                  onClick={() => {
                    onPageChange(page);
                  }}
                >
                  {page}
                </button>
              </li>
            ),
          )}
        </ol>
        <button
          type="button"
          disabled={pageNumber >= totalPages}
          onClick={() => {
            onPageChange(pageNumber + 1);
          }}
          aria-label="Next page"
        >
          <CaretRightIcon size={16} />
        </button>
      </nav>
    </div>
  );
}

function paginationItems(
  current: number,
  total: number,
): (number | "ellipsis")[] {
  if (total <= 7) {
    return Array.from({ length: total }, (_, index) => index + 1);
  }
  if (current <= 4) {
    return [1, 2, 3, 4, "ellipsis", total - 1, total];
  }
  if (current >= total - 3) {
    return [1, 2, "ellipsis", total - 3, total - 2, total - 1, total];
  }
  return [1, "ellipsis", current - 1, current, current + 1, "ellipsis", total];
}

function providerTier(
  item: PackageSummary,
): "official" | "partner" | "community" {
  if (item.registryTier === "official") return "official";
  if (
    item.registryTier === "partner" ||
    item.registryTier === "partner-premier"
  ) {
    return "partner";
  }
  return "community";
}

function browseTabHref(
  kind: PackageKind,
  q: string,
  namespace?: string,
): string {
  const path = kind === "provider" ? "/providers" : "/modules";
  const query = new URLSearchParams();
  if (q) query.set("q", q);
  if (hasText(namespace)) query.set("namespace", namespace);
  const serialized = query.toString();
  return serialized ? `${path}?${serialized}` : path;
}

function filterNoun(kind?: PackageKind): string {
  if (kind === "provider") return "Providers";
  if (kind === "module") return "Modules";
  return "Packages";
}

function enumParam<T extends string>(
  value: string | null,
  values: readonly T[],
): T | undefined {
  return value !== null && values.includes(value as T)
    ? (value as T)
    : undefined;
}

function csvParam(
  value: string | null,
  allowed: readonly string[],
): string | undefined {
  if (value === null) return undefined;
  const values = value
    .split(",")
    .filter((candidate) => allowed.includes(candidate));
  return values.length > 0 ? [...new Set(values)].join(",") : undefined;
}

function positiveIntegerParam(value: string | null): number {
  if (value === null) return 1;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 && parsed <= 10_000
    ? parsed
    : 1;
}
