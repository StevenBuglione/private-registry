import { ArrowLeftIcon, ArrowRightIcon, ListIcon } from "@phosphor-icons/react";
import { useMemo, useState } from "react";
import { NavLink, useSearchParams } from "react-router";
import { type FilterState, Filters } from "../components/Filters";
import { PackageCard } from "../components/PackageCard";
import { StatePanel } from "../components/StatePanel";
import { useCatalogPage } from "../hooks";
import type { Approval, Lifecycle, PackageKind, Risk } from "../types";
import { useRegistry } from "../use-registry";
import { hasText } from "../utils";

export function CatalogPage({ kind }: { kind?: PackageKind }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const { selectedApmId } = useRegistry();
  const [mobileFilters, setMobileFilters] = useState(false);
  const filters: FilterState = useMemo(
    () => ({
      kind:
        kind ??
        enumParam<PackageKind>(searchParams.get("kind"), [
          "provider",
          "module",
        ]),
      provider: searchParams.get("provider") ?? undefined,
      lifecycle: enumParam<Lifecycle>(searchParams.get("lifecycle"), [
        "draft",
        "candidate",
        "approved",
        "maintenance",
        "deprecated",
        "revoked",
        "archived",
      ]),
      approval: enumParam<Approval>(searchParams.get("approval"), [
        "approved",
        "rejected",
        "waived",
      ]),
      risk: enumParam<Risk>(searchParams.get("risk"), [
        "low",
        "medium",
        "high",
        "critical",
      ]),
    }),
    [kind, searchParams],
  );
  const q = searchParams.get("q") ?? "";
  const sort =
    enumParam(searchParams.get("sort"), [
      "relevance",
      "updated",
      "name",
      "risk",
    ] as const) ?? (q ? "relevance" : "updated");
  const cursorScope = [
    q,
    filters.kind,
    filters.provider,
    filters.lifecycle,
    filters.approval,
    filters.risk,
    sort,
    selectedApmId,
  ].join("|");
  const [cursorState, setCursorState] = useState<{
    scope: string;
    values: string[];
  }>({ scope: cursorScope, values: [] });
  if (cursorState.scope !== cursorScope)
    setCursorState({ scope: cursorScope, values: [] });
  const cursorHistory =
    cursorState.scope === cursorScope ? cursorState.values : [];
  const cursor = cursorHistory.at(-1);
  const result = useCatalogPage({
    ...filters,
    q,
    sort,
    apmId: selectedApmId,
    cursor,
    limit: 20,
  });
  const nextCursor = result.data?.nextCursor;

  const updateParam = (key: string, value?: string) => {
    const next = new URLSearchParams(searchParams);
    if (hasText(value)) next.set(key, value);
    else next.delete(key);
    setSearchParams(next, { replace: true });
  };
  const updateFilter = <K extends keyof FilterState>(
    key: K,
    value?: FilterState[K],
  ) => {
    updateParam(key === "kind" ? "kind" : key, value);
  };
  const clear = () => {
    const next = new URLSearchParams();
    if (q) next.set("q", q);
    setSearchParams(next, { replace: true });
  };

  const title =
    kind === "provider"
      ? "Providers"
      : kind === "module"
        ? "Modules"
        : q
          ? `Results for “${q}”`
          : "Browse the catalog";
  const description =
    kind === "provider"
      ? "Providers are a logical abstraction of an upstream API. They expose approved infrastructure resources and services."
      : kind === "module"
        ? "Modules are self-contained packages of infrastructure configuration that are managed and versioned as a group."
        : "Search and filter every provider and module available through your APM memberships.";

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
        <NavLink to="/providers">Providers</NavLink>
        <NavLink to="/modules">Modules</NavLink>
        <NavLink to="/browse">All Packages</NavLink>
      </nav>
      <button
        className="mobile-filter-button"
        type="button"
        onClick={() => {
          setMobileFilters((value) => !value);
        }}
        aria-expanded={mobileFilters}
      >
        <ListIcon size={18} /> Filter{" "}
        {kind === "provider"
          ? "Providers"
          : kind === "module"
            ? "Modules"
            : "Packages"}
      </button>
      <div className="catalog-layout">
        <Filters
          value={filters}
          kindLocked={kind}
          mobileOpen={mobileFilters}
          onMobileToggle={() => {
            setMobileFilters((value) => !value);
          }}
          onChange={updateFilter}
          onClear={clear}
        />
        <div className="catalog-content">
          <header className="catalog-heading">
            <div>
              <p className="eyebrow">Authorized catalog</p>
              <h1>{title}</h1>
              <p>{description}</p>
            </div>
            <label className="sort-select">
              <span>Sort by</span>
              <select
                value={sort}
                onChange={(event) => {
                  updateParam("sort", event.target.value);
                }}
              >
                <option value="relevance">Relevance</option>
                <option value="updated">Recently updated</option>
                <option value="name">Name</option>
                <option value="risk">Risk</option>
              </select>
            </label>
          </header>
          <div className="result-summary" aria-live="polite">
            <strong>{result.data?.total ?? 0}</strong> authorized{" "}
            {kind ? `${kind}s` : "packages"}
            {hasText(selectedApmId) ? <span>for {selectedApmId}</span> : null}
          </div>
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
            <section className="catalog-result-section">
              <h2>
                {kind === "provider" && !q ? "Featured Providers" : "Providers"}
              </h2>
              <div className="source-card-grid providers">
                {providerItems.map((item) => (
                  <PackageCard
                    key={`${item.namespace}-${item.name}`}
                    item={item}
                  />
                ))}
              </div>
            </section>
          ) : null}
          {moduleItems.length > 0 ? (
            <section className="catalog-result-section">
              {providerItems.length > 0 ? <h2>Modules</h2> : null}
              <div className="source-card-grid modules">
                {moduleItems.map((item) => (
                  <PackageCard
                    key={`${item.namespace}-${item.name}-${item.target ?? "general"}`}
                    item={item}
                  />
                ))}
              </div>
            </section>
          ) : null}
          {(result.data?.items.length ?? 0) > 0 ? (
            <nav className="pagination" aria-label="Catalog pages">
              <button
                type="button"
                disabled={cursorHistory.length === 0}
                onClick={() => {
                  setCursorState((state) => ({
                    ...state,
                    values: state.values.slice(0, -1),
                  }));
                }}
              >
                <ArrowLeftIcon size={16} /> Previous
              </button>
              <span>
                Showing {cursorHistory.length * 20 + 1}–
                {cursorHistory.length * 20 + (result.data?.items.length ?? 0)}{" "}
                of {result.data?.total ?? 0}
              </span>
              <button
                type="button"
                disabled={!hasText(nextCursor)}
                onClick={() => {
                  if (hasText(nextCursor)) {
                    setCursorState((state) => ({
                      ...state,
                      values: [...state.values, nextCursor],
                    }));
                  }
                }}
              >
                Next <ArrowRightIcon size={16} />
              </button>
            </nav>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function enumParam<T extends string>(
  value: string | null,
  values: readonly T[],
): T | undefined {
  return value !== null && values.includes(value as T)
    ? (value as T)
    : undefined;
}
