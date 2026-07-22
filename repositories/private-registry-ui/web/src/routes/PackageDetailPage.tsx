import {
  ArrowSquareOutIcon,
  CaretRightIcon,
  CheckIcon,
  ClipboardIcon,
  CubeIcon,
  FileTextIcon,
  InfoIcon,
  LinkSimpleIcon,
  ListIcon,
  MagnifyingGlassIcon,
  ShieldCheckIcon,
  WarningIcon,
} from "@phosphor-icons/react";
import { Children, useMemo, useState, type ReactNode } from "react";
import ReactMarkdown from "react-markdown";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import rehypeSanitize from "rehype-sanitize";
import remarkGfm from "remark-gfm";
import { ApiError } from "../api";
import { ApprovalBadge } from "../components/Badges";
import { PackageIcon } from "../components/PackageIcon";
import { StatePanel } from "../components/StatePanel";
import {
  useCatalogPage,
  usePackage,
  usePackageDocumentation,
  usePackageGovernance,
} from "../hooks";
import { useRegistry } from "../registry-context";
import { runtimeConfig } from "../runtime-config";
import type {
  GovernanceRecord,
  PackageDetail,
  PackageKind,
  PackageSummary,
  PackageSymbol,
} from "../types";
import { formatRelativeDate, packageHref } from "../utils";

export function PackageDetailPage({ kind }: { kind: PackageKind }) {
  const params = useParams();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { selectedApmId } = useRegistry();
  const identity = {
    kind,
    namespace: params.namespace ?? "",
    name: params.name ?? "",
    target: kind === "module" ? params.target : undefined,
    version: params.version,
    apmId: selectedApmId,
  };
  const defaultTab = kind === "provider" ? "overview" : "readme";
  const tab = searchParams.get("tab") ?? defaultTab;
  const documentPath =
    kind === "provider" && tab === "documentation"
      ? (searchParams.get("doc") ?? undefined)
      : undefined;
  const detail = usePackage(identity);
  const documentation = usePackageDocumentation(
    identity,
    detail.data?.documentation,
    documentPath,
  );
  const governance = usePackageGovernance(identity, detail.data?.governance);
  const related = useCatalogPage({
    kind: "module",
    provider: detail.data?.provider,
    apmId: selectedApmId,
    approval: "approved",
    limit: 4,
  });

  if (detail.isPending)
    return (
      <div className="source-container">
        <div className="detail-loading skeleton" />
      </div>
    );
  if (detail.isError) {
    const notFound =
      detail.error instanceof ApiError &&
      (detail.error.status === 404 || detail.error.status === 403);
    return (
      <div className="source-container">
        <StatePanel
          kind={notFound ? "not-found" : "api-error"}
          action={notFound ? undefined : () => void detail.refetch()}
        />
      </div>
    );
  }

  const item = detail.data;
  const docs =
    documentation.data ??
    (documentPath ? undefined : item.documentation) ??
    `# ${item.name}\n\nDocumentation has not been published for this package version.`;
  const governanceData = governance.data ?? item.governance;
  const installSnippet = buildInstallSnippet(
    item,
    runtimeConfig().jfrogHostname,
  );
  const providerSnippet = buildProviderConfigurationSnippet(item);
  const sourceRepository = safeExternalUrl(governanceData?.sourceRepository);
  const setTab = (value: string) => {
    const next = new URLSearchParams(searchParams);
    next.set("tab", value);
    if (value !== "documentation") next.delete("doc");
    setSearchParams(next);
  };
  const selectDocument = (path?: string) => {
    const next = new URLSearchParams(searchParams);
    next.set("tab", "documentation");
    if (path) next.set("doc", path);
    else next.delete("doc");
    setSearchParams(next);
  };
  const changeVersion = (version: string) => {
    const destination = packageHref({ ...item, version });
    const query = searchParams.toString();
    navigate(query ? `${destination}?${query}` : destination);
  };
  const showDocumentation = kind === "provider" && tab === "documentation";

  return (
    <div className="detail-page">
      <header className="package-source-header source-container">
        <nav className="source-breadcrumbs" aria-label="Breadcrumb">
          <Link to={kind === "provider" ? "/providers" : "/modules"}>
            {kind === "provider" ? "Providers" : "Modules"}
          </Link>
          <CaretRightIcon size={12} />
          <span>{item.namespace}</span>
          <CaretRightIcon size={12} />
          <span>{item.name}</span>
          <CaretRightIcon size={12} />
          <span>v{item.version}</span>
        </nav>
        <div className="package-title-row">
          <PackageIcon kind={kind} name={item.name} size="large" />
          <div>
            <div className="package-name-line">
              <h1>{item.name}</h1>
              <ApprovalBadge value={item.approval} verified={item.verified} />
            </div>
            <span>
              {item.namespace}/{item.name}
              {item.target ? `/${item.target}` : ""}
            </span>
          </div>
          <div className="package-header-actions">
            <label className="version-select">
              <span>Version</span>
              <select
                aria-label={`${kind === "provider" ? "Provider" : "Module"} version`}
                value={item.version}
                onChange={(event) => changeVersion(event.target.value)}
              >
                {item.versions.map((version) => (
                  <option key={version} value={version}>
                    Version {version}
                    {version === item.versions[0] ? " (latest)" : ""}
                  </option>
                ))}
              </select>
            </label>
            {sourceRepository ? (
              <a
                className="view-source-button"
                href={sourceRepository}
                target="_blank"
                rel="noreferrer"
              >
                <ArrowSquareOutIcon size={16} /> View Source
              </a>
            ) : null}
          </div>
        </div>
        <p className="package-description">{item.description}</p>
        <div className="package-facts">
          <span>
            Versions: <strong>{item.versions.length}</strong>
          </span>
          <span>
            Owner: <strong>{governanceData?.owner ?? item.owner}</strong>
          </span>
          {sourceRepository ? (
            <span>
              Source code:{" "}
              <a href={sourceRepository} target="_blank" rel="noreferrer">
                {item.namespace}/{item.name}
              </a>
            </span>
          ) : null}
          <span>
            Lifecycle: <strong>{item.lifecycle}</strong>
          </span>
          <span>
            Published: <strong>{formatCalendarDate(item.updatedAt)}</strong>
          </span>
          <span>
            Risk: <strong>{item.risk}</strong>
          </span>
        </div>
        <span className="package-category">{capitalize(item.lifecycle)}</span>
      </header>

      <nav
        className="package-tabs source-container"
        aria-label="Package sections"
      >
        {kind === "provider" ? (
          <>
            <button
              className={tab === "overview" ? "active" : ""}
              type="button"
              onClick={() => setTab("overview")}
            >
              Overview
            </button>
            <button
              className={tab === "documentation" ? "active" : ""}
              type="button"
              onClick={() => setTab("documentation")}
            >
              Documentation
            </button>
          </>
        ) : (
          ["readme", "inputs", "outputs", "dependencies", "resources"].map(
            (value) => (
              <button
                key={value}
                className={tab === value ? "active" : ""}
                type="button"
                aria-label={
                  value === "readme"
                    ? "Readme"
                    : `${capitalize(value)} (${moduleTabCount(item.symbols, value)})`
                }
                onClick={() => setTab(value)}
              >
                {capitalize(value)}
                {value !== "readme" ? (
                  <span className="tab-count">
                    {moduleTabCount(item.symbols, value)}
                  </span>
                ) : null}
              </button>
            ),
          )
        )}
      </nav>

      {showDocumentation ? (
        <ProviderDocumentation
          docs={docs}
          packageName={item.name}
          symbols={item.symbols}
          selectedPath={documentPath}
          pending={documentation.isPending}
          failed={documentation.isError}
          onRetry={() => void documentation.refetch()}
          onSelect={selectDocument}
        />
      ) : (
        <div className="package-content-surface">
          {kind === "provider" && tab === "overview" ? (
            <ProviderOverview
              item={item}
              modules={related.data?.items ?? []}
              moduleTotal={related.data?.total ?? 0}
              snippet={providerSnippet}
              governance={governanceData}
              selectedApmId={selectedApmId}
              sourceRepository={sourceRepository}
            />
          ) : (
            <div className="source-container package-overview-grid module-overview-grid">
              <main>
                {kind === "module" ? (
                  <ModuleTabContent
                    tab={tab}
                    docs={docs}
                    symbols={item.symbols}
                  />
                ) : (
                  <MarkdownDocument docs={docs} className="source-readme" />
                )}
              </main>
              <aside
                className="source-install-sidebar"
                aria-label="Installation and governance"
              >
                <InstallPanel snippet={installSnippet} kind={kind} />
                <GovernanceCard
                  item={item}
                  governance={governanceData}
                  selectedApmId={selectedApmId}
                />
              </aside>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function ProviderOverview({
  item,
  modules,
  moduleTotal,
  snippet,
  governance,
  selectedApmId,
  sourceRepository,
}: {
  item: PackageDetail;
  modules: PackageSummary[];
  moduleTotal: number;
  snippet: string;
  governance?: GovernanceRecord;
  selectedApmId?: string;
  sourceRepository?: string;
}) {
  const supportUrl = safeExternalUrl(runtimeConfig().supportUrl);
  return (
    <div className="source-container provider-overview-grid">
      <main>
        <div className="content-title-row provider-modules-heading">
          <h2>
            <CubeIcon size={20} /> Approved {item.name} modules
          </h2>
          <Link to={`/modules?provider=${encodeURIComponent(item.provider)}`}>
            View all modules <CaretRightIcon size={14} />
          </Link>
        </div>
        <p className="provider-overview-intro">
          Modules are self-contained packages of Terraform configurations that
          are managed as a group.
        </p>
        {modules.length ? (
          <>
            <p className="provider-module-count">
              Showing 1 - {modules.length} of{" "}
              {Math.max(moduleTotal, modules.length)} available modules
            </p>
            <div className="provider-module-list">
              {modules.map((module) => (
                <Link
                  className="provider-module-row"
                  key={`${module.namespace}-${module.name}-${module.target}`}
                  to={packageHref(module)}
                >
                  <PackageIcon kind="module" name={module.provider} />
                  <div>
                    <h3>
                      <span>{module.namespace}</span>
                      <b>/</b>
                      {module.name}
                    </h3>
                    <p>{module.description}</p>
                    <small>
                      {formatRelativeDate(module.updatedAt)}
                      <span>v{module.version}</span>
                    </small>
                  </div>
                </Link>
              ))}
            </div>
          </>
        ) : (
          <StatePanel kind="empty" />
        )}
        <InstallPanel
          snippet={snippet}
          kind="provider"
          artifactLabel={
            item.artifactRepository && item.artifactPath
              ? `${item.artifactRepository}/${item.artifactPath}`
              : item.artifactRepository
          }
        />
      </main>
      <aside
        className="provider-overview-sidebar"
        aria-label="Provider details"
      >
        <section className="provider-helpful-links">
          <h2>Helpful Links</h2>
          <nav aria-label="Provider links">
            {sourceRepository ? (
              <a href={sourceRepository} target="_blank" rel="noreferrer">
                Source Code <ArrowSquareOutIcon size={14} />
              </a>
            ) : null}
            <Link to="?tab=documentation">
              Provider Documentation <CaretRightIcon size={14} />
            </Link>
            <Link to={`/modules?provider=${encodeURIComponent(item.provider)}`}>
              Approved Modules <CaretRightIcon size={14} />
            </Link>
            {supportUrl ? (
              <a href={supportUrl} target="_blank" rel="noreferrer">
                Registry Support <ArrowSquareOutIcon size={14} />
              </a>
            ) : null}
          </nav>
        </section>
        <section className="provider-versions-card">
          <header>
            <h2>Provider Versions</h2>
            <span>{item.versions.length}</span>
          </header>
          <ul>
            {item.versions.map((version, index) => (
              <li key={version}>
                <span>Version {version}</span>
                {index === 0 ? <strong>Latest</strong> : null}
              </li>
            ))}
          </ul>
        </section>
        <GovernanceCard
          item={item}
          governance={governance}
          selectedApmId={selectedApmId}
        />
      </aside>
    </div>
  );
}

function GovernanceCard({
  item,
  governance,
  selectedApmId,
}: {
  item: PackageDetail;
  governance?: GovernanceRecord;
  selectedApmId?: string;
}) {
  return (
    <section className="source-governance-card">
      <h2>
        <ShieldCheckIcon size={18} /> Governance
      </h2>
      <dl>
        <div>
          <dt>Owner</dt>
          <dd>{governance?.owner ?? item.owner}</dd>
        </div>
        <div>
          <dt>Support</dt>
          <dd>{governance?.support ?? "Internal support"}</dd>
        </div>
        <div>
          <dt>APM access</dt>
          <dd>
            {(governance?.apmIds.length ? governance.apmIds : item.apmIds).join(
              ", ",
            ) ||
              selectedApmId ||
              "Administrator"}
          </dd>
        </div>
      </dl>
    </section>
  );
}

function ProviderDocumentation({
  docs,
  packageName,
  symbols,
  selectedPath,
  pending,
  failed,
  onRetry,
  onSelect,
}: {
  docs: string;
  packageName: string;
  symbols: PackageSymbol[];
  selectedPath?: string;
  pending: boolean;
  failed: boolean;
  onRetry: () => void;
  onSelect: (path?: string) => void;
}) {
  const [browseOpen, setBrowseOpen] = useState(false);
  const [filter, setFilter] = useState("");
  const groups = useMemo(
    () => providerDocumentGroups(symbols, filter),
    [filter, symbols],
  );
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(() => {
    const selectedGroup = providerDocumentGroups(symbols, "").find((group) =>
      group.items.some((symbol) => symbol.path === selectedPath),
    );
    return selectedGroup ? new Set([selectedGroup.label]) : new Set();
  });
  const headings = extractMarkdownHeadings(docs).filter(
    (heading) => heading.level > 1,
  );
  const matchingDocumentCount = groups.reduce(
    (count, group) => count + group.items.length,
    0,
  );
  const autoExpandedGroups = useMemo(() => {
    if (filter.trim()) {
      return new Set(
        groups
          .filter((group) => group.items.length > 0)
          .map((group) => group.label),
      );
    }
    return undefined;
  }, [filter, groups]);
  const isGroupExpanded = (label: string) =>
    (autoExpandedGroups ?? expandedGroups).has(label);
  const select = (path?: string) => {
    onSelect(path);
    setBrowseOpen(false);
  };
  const toggleGroup = (label: string) => {
    setExpandedGroups((current) => {
      const next = new Set(current);
      if (next.has(label)) next.delete(label);
      else next.add(label);
      return next;
    });
  };
  return (
    <div className="provider-docs-layout source-container">
      <button
        className="mobile-docs-button"
        type="button"
        onClick={() => setBrowseOpen((value) => !value)}
        aria-expanded={browseOpen}
      >
        <ListIcon size={18} /> Browse {packageName} documentation
      </button>
      <aside
        className={`provider-docs-nav ${browseOpen ? "is-open" : ""}`}
        aria-label="Provider documentation navigation"
      >
        <div className="provider-docs-nav-header">
          <strong>{packageName} documentation</strong>
          <label className="docs-filter">
            <MagnifyingGlassIcon size={15} />
            <input
              aria-label="Filter documentation"
              placeholder="Filter"
              value={filter}
              onChange={(event) => setFilter(event.target.value)}
            />
          </label>
          <button
            type="button"
            className={!selectedPath ? "active" : ""}
            aria-current={!selectedPath ? "page" : undefined}
            onClick={() => select()}
          >
            {packageName} provider
          </button>
          {filter.trim() ? (
            <span className="docs-result-count">
              {matchingDocumentCount} matching result
              {matchingDocumentCount === 1 ? "" : "s"}
            </span>
          ) : null}
        </div>
        {groups.map((group) => (
          <section className="docs-nav-group" key={group.label}>
            <h2>
              <button
                type="button"
                className="docs-group-toggle"
                aria-expanded={isGroupExpanded(group.label)}
                onClick={() => toggleGroup(group.label)}
              >
                <CaretRightIcon
                  className={isGroupExpanded(group.label) ? "expanded" : ""}
                  size={13}
                />
                {group.label}
                <span>{group.items.length}</span>
              </button>
            </h2>
            {isGroupExpanded(group.label)
              ? group.items.map((symbol) => (
                  <button
                    type="button"
                    key={`${symbol.kind}-${symbol.name}-${symbol.path}`}
                    className={selectedPath === symbol.path ? "active" : ""}
                    aria-current={
                      selectedPath === symbol.path ? "page" : undefined
                    }
                    title={symbol.description}
                    onClick={() => {
                      setExpandedGroups(new Set([group.label]));
                      select(symbol.path);
                    }}
                  >
                    {displayProviderSymbolName(packageName, symbol)}
                  </button>
                ))
              : null}
          </section>
        ))}
        {filter && groups.every((group) => group.items.length === 0) ? (
          <p className="docs-filter-empty">No documentation matches.</p>
        ) : null}
      </aside>
      {pending ? (
        <div className="documentation documentation-loading skeleton" />
      ) : failed ? (
        <div className="documentation">
          <StatePanel kind="api-error" action={onRetry} />
        </div>
      ) : (
        <MarkdownDocument docs={docs} />
      )}
      <aside className="on-this-page" aria-label="On this page">
        <strong>
          <FileTextIcon size={15} /> On this page
        </strong>
        {headings.slice(0, 10).map((heading) => (
          <a
            key={`${heading.level}-${heading.id}`}
            className={`heading-level-${heading.level}`}
            href={`#${heading.id}`}
          >
            {heading.title}
          </a>
        ))}
      </aside>
    </div>
  );
}

function ModuleTabContent({
  tab,
  docs,
  symbols,
}: {
  tab: string;
  docs: string;
  symbols: PackageSymbol[];
}) {
  if (tab === "readme")
    return <MarkdownDocument docs={docs} className="source-readme" />;

  const items = symbolsForModuleTab(symbols, tab);
  const title = capitalize(tab);
  if (!items.length)
    return (
      <section className="module-symbol-panel">
        <div className="symbol-panel-heading">
          <h2>{title}</h2>
          <span>0</span>
        </div>
        <p className="symbol-empty">
          No declared {tab} metadata is published for this module version.
        </p>
      </section>
    );

  if (tab === "inputs") return <InputDefinitions symbols={items} />;
  if (tab === "outputs") return <OutputDefinitions symbols={items} />;
  return <SymbolList title={title} symbols={items} />;
}

function InputDefinitions({ symbols }: { symbols: PackageSymbol[] }) {
  return (
    <section className="module-symbol-panel">
      <div className="symbol-panel-heading">
        <div>
          <h2>Inputs</h2>
          <p>Configuration values accepted by this module version.</p>
        </div>
        <span>{symbols.length}</span>
      </div>
      <div className="symbol-table-wrap" role="region" tabIndex={0}>
        <table className="symbol-table">
          <caption>Module input definitions</caption>
          <thead>
            <tr>
              <th scope="col">Name</th>
              <th scope="col">Description</th>
              <th scope="col">Type</th>
              <th scope="col">Default</th>
              <th scope="col">Required</th>
              <th scope="col">Sensitive</th>
            </tr>
          </thead>
          <tbody>
            {symbols.map((symbol) => (
              <tr key={`${symbol.name}-${symbol.path}`}>
                <th scope="row">
                  <code>{symbol.name}</code>
                </th>
                <td>{symbol.description ?? "No description published."}</td>
                <td>
                  <code>{symbol.type ?? "Unknown"}</code>
                </td>
                <td>
                  <code>{formatDefaultValue(symbol.defaultValue)}</code>
                </td>
                <td>{formatBoolean(symbol.required)}</td>
                <td>{formatBoolean(symbol.sensitive)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function OutputDefinitions({ symbols }: { symbols: PackageSymbol[] }) {
  return (
    <section className="module-symbol-panel">
      <div className="symbol-panel-heading">
        <div>
          <h2>Outputs</h2>
          <p>Values exported for use by other configurations.</p>
        </div>
        <span>{symbols.length}</span>
      </div>
      <div className="symbol-table-wrap" role="region" tabIndex={0}>
        <table className="symbol-table output-table">
          <caption>Module output definitions</caption>
          <thead>
            <tr>
              <th scope="col">Name</th>
              <th scope="col">Description</th>
              <th scope="col">Type</th>
              <th scope="col">Sensitive</th>
            </tr>
          </thead>
          <tbody>
            {symbols.map((symbol) => (
              <tr key={`${symbol.name}-${symbol.path}`}>
                <th scope="row">
                  <code>{symbol.name}</code>
                </th>
                <td>{symbol.description ?? "No description published."}</td>
                <td>
                  <code>{symbol.type ?? "Unknown"}</code>
                </td>
                <td>{formatBoolean(symbol.sensitive)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function SymbolList({
  title,
  symbols,
}: {
  title: string;
  symbols: PackageSymbol[];
}) {
  return (
    <section className="module-symbol-panel">
      <div className="symbol-panel-heading">
        <div>
          <h2>{title}</h2>
          <p>
            {title === "Dependencies"
              ? "External providers and modules required by this version."
              : "Infrastructure objects declared by this module version."}
          </p>
        </div>
        <span>{symbols.length}</span>
      </div>
      <ul className="symbol-list">
        {symbols.map((symbol) => {
          const dependency = title === "Dependencies";
          const source = dependency
            ? (symbol.source ?? symbol.description)
            : symbol.path;
          return (
            <li key={`${symbol.kind}-${symbol.name}-${symbol.path}`}>
              <div>
                <code>{symbol.name}</code>
                <span className="symbol-kind">
                  {symbolKindLabel(symbol.kind)}
                </span>
              </div>
              <p>
                {dependency
                  ? dependencyDescription(symbol)
                  : (symbol.description ?? "No description published.")}
              </p>
              <dl>
                <div>
                  <dt>{dependency ? "Kind" : "Provider"}</dt>
                  <dd>
                    {dependency
                      ? dependencyKind(symbol)
                      : resourceProvider(symbol)}
                  </dd>
                </div>
                <div>
                  <dt>{dependency ? "Source" : "Declared in"}</dt>
                  <dd>{source ?? "Unknown"}</dd>
                </div>
                {dependency && symbol.defaultValue !== undefined ? (
                  <div>
                    <dt>Version</dt>
                    <dd>{formatDefaultValue(symbol.defaultValue)}</dd>
                  </div>
                ) : null}
              </dl>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function dependencyDescription(symbol: PackageSymbol): string {
  const kind = normalizeSymbolKind(symbol.kind);
  if (symbol.type === "provider" || kind.includes("provider"))
    return `Provider requirement for ${symbol.source ?? symbol.description ?? symbol.name}.`;
  if (symbol.type === "module" || kind.includes("module"))
    return `Module dependency on ${symbol.source ?? symbol.description ?? symbol.name}.`;
  return symbol.description ?? "External dependency declared by this module.";
}

function dependencyKind(symbol: PackageSymbol): string {
  if (symbol.type) return capitalize(symbol.type);
  const kind = normalizeSymbolKind(symbol.kind);
  if (kind.includes("provider")) return "Provider";
  if (kind.includes("module")) return "Module";
  return "Dependency";
}

function resourceProvider(symbol: PackageSymbol): string {
  if (symbol.provider) return symbol.provider;
  const resourceType = symbol.type ?? symbol.name.split(".")[0];
  const separator = resourceType.indexOf("_");
  return separator > 0 ? resourceType.slice(0, separator) : resourceType;
}

function MarkdownDocument({
  docs,
  className = "",
}: {
  docs: string;
  className?: string;
}) {
  return (
    <article className={`documentation ${className}`.trim()} id="overview">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeSanitize]}
        components={{
          h1: ({ children }) => <h1 id={slugText(children)}>{children}</h1>,
          h2: ({ children }) => {
            const id = slugText(children);
            return (
              <h2 id={id}>
                <a className="heading-self-link" href={`#${id}`}>
                  {children}
                  <LinkSimpleIcon aria-hidden="true" size={15} />
                </a>
              </h2>
            );
          },
          h3: ({ children }) => {
            const id = slugText(children);
            return (
              <h3 id={id}>
                <a className="heading-self-link" href={`#${id}`}>
                  {children}
                  <LinkSimpleIcon aria-hidden="true" size={14} />
                </a>
              </h3>
            );
          },
          p: MarkdownParagraph,
          pre: MarkdownPre,
        }}
      >
        {docs}
      </ReactMarkdown>
    </article>
  );
}

function MarkdownParagraph({ children }: { children?: ReactNode }) {
  const parts = Children.toArray(children);
  const leading = typeof parts[0] === "string" ? parts[0] : "";
  const isInfo = /^\s*->\s*/.test(leading);
  const isWarning = /^\s*~>\s*/.test(leading);
  if (!isInfo && !isWarning) return <p>{children}</p>;
  const rest = [leading.replace(/^\s*(?:->|~>)\s*/, ""), ...parts.slice(1)];
  return (
    <aside
      className={`docs-callout ${isWarning ? "warning" : "info"}`}
      role="note"
    >
      {isWarning ? (
        <WarningIcon aria-hidden="true" size={18} weight="fill" />
      ) : (
        <InfoIcon aria-hidden="true" size={18} weight="fill" />
      )}
      <p>{rest}</p>
    </aside>
  );
}

function MarkdownPre({ children }: { children?: ReactNode }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(reactNodeText(children));
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1800);
  };
  return (
    <div className="markdown-code-block">
      <button type="button" onClick={() => void copy()}>
        {copied ? <CheckIcon size={14} /> : <ClipboardIcon size={14} />}
        {copied ? "Copied" : "Copy"}
      </button>
      <pre>{children}</pre>
    </div>
  );
}

function reactNodeText(node: ReactNode): string {
  return Children.toArray(node)
    .map((child) => {
      if (typeof child === "string" || typeof child === "number") {
        return String(child);
      }
      if (
        child &&
        typeof child === "object" &&
        "props" in child &&
        child.props &&
        typeof child.props === "object" &&
        "children" in child.props
      ) {
        return reactNodeText(child.props.children as ReactNode);
      }
      return "";
    })
    .join("");
}

function moduleTabCount(symbols: PackageSymbol[], tab: string): number {
  return symbolsForModuleTab(symbols, tab).length;
}

function symbolsForModuleTab(
  symbols: PackageSymbol[],
  tab: string,
): PackageSymbol[] {
  const accepted: Record<string, string[]> = {
    inputs: ["input", "variable"],
    outputs: ["output"],
    dependencies: [
      "dependency",
      "module_dependency",
      "provider_dependency",
      "provider_requirement",
    ],
    resources: ["resource", "data_source", "datasource", "module_call"],
  };
  const kinds = accepted[tab] ?? [];
  return symbols.filter((symbol) =>
    kinds.includes(normalizeSymbolKind(symbol.kind)),
  );
}

function providerDocumentGroups(symbols: PackageSymbol[], filter: string) {
  const normalizedFilter = filter.trim().toLowerCase();
  const matches = (symbol: PackageSymbol) =>
    !normalizedFilter ||
    `${symbol.name} ${symbol.description ?? ""}`
      .toLowerCase()
      .includes(normalizedFilter);
  const definitions = [
    { label: "Guides", kinds: ["guide", "document", "overview"] },
    { label: "Resources", kinds: ["resource"] },
    { label: "Data Sources", kinds: ["data_source", "datasource"] },
    { label: "Functions", kinds: ["function"] },
  ];
  const recognized = new Set(definitions.flatMap((group) => group.kinds));
  return definitions.map((group) => ({
    label: group.label,
    items: symbols.filter((symbol) => {
      const kind = normalizeSymbolKind(symbol.kind);
      const belongs =
        group.kinds.includes(kind) ||
        (group.label === "Guides" &&
          !recognized.has(kind) &&
          !["input", "output", "dependency"].includes(kind));
      return belongs && matches(symbol);
    }),
  }));
}

function normalizeSymbolKind(kind: string): string {
  return kind
    .trim()
    .toLowerCase()
    .replaceAll(/[\s-]+/g, "_");
}

function symbolKindLabel(kind: string): string {
  return normalizeSymbolKind(kind).split("_").map(capitalize).join(" ");
}

function displayProviderSymbolName(
  packageName: string,
  symbol: PackageSymbol,
): string {
  const kind = normalizeSymbolKind(symbol.kind);
  if (
    ["resource", "data_source", "datasource"].includes(kind) &&
    !symbol.name.startsWith(`${packageName}_`)
  ) {
    return `${packageName}_${symbol.name}`;
  }
  return symbol.name;
}

function formatDefaultValue(value: unknown): string {
  if (value === undefined) return "Unknown";
  if (typeof value === "string") return value || '""';
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function formatBoolean(value?: boolean): string {
  return value === undefined ? "Unknown" : value ? "Yes" : "No";
}

function extractMarkdownHeadings(markdown: string) {
  return markdown
    .split(/\r?\n/)
    .map((line) => /^(#{1,3})\s+(.+?)\s*#*\s*$/.exec(line))
    .filter((match): match is RegExpExecArray => match !== null)
    .map((match) => {
      const title = match[2].replaceAll(/[*_`[\]]/g, "").trim();
      return { level: match[1].length, title, id: slugText(title) };
    });
}

function slugText(value: unknown): string {
  return String(value)
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/g, "-")
    .replaceAll(/(^-|-$)/g, "");
}

function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function formatCalendarDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en-US", {
    month: "long",
    day: "numeric",
    year: "numeric",
  }).format(date);
}

function safeExternalUrl(value?: string): string | undefined {
  if (!value) return undefined;
  try {
    const url = new URL(value);
    if (
      !["https:", "http:"].includes(url.protocol) ||
      url.hostname.endsWith(".invalid")
    ) {
      return undefined;
    }
    return url.toString();
  } catch {
    return undefined;
  }
}

function InstallPanel({
  snippet,
  kind,
  artifactLabel,
}: {
  snippet: string;
  kind: PackageKind;
  artifactLabel?: string;
}) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(snippet);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1800);
  };
  return (
    <section className="source-install-card">
      <h2>
        {kind === "provider"
          ? "How to use this provider"
          : "Provision instructions"}
      </h2>
      <p>
        {kind === "provider" ? (
          <>
            To install this provider, copy and paste this code into your
            Terraform configuration. Then, run <code>terraform init</code>.
          </>
        ) : (
          "Copy this approved configuration into your project, then initialize your workspace."
        )}
      </p>
      {kind === "provider" ? <strong>Terraform 0.13+</strong> : null}
      <pre>
        <code>{snippet}</code>
      </pre>
      <button type="button" onClick={() => void copy()}>
        {copied ? <CheckIcon size={16} /> : <ClipboardIcon size={16} />}
        {copied ? "Copied" : "Copy"}
      </button>
      {artifactLabel ? (
        <small className="artifact-source-note">
          Approved binary source: <code>{artifactLabel}</code>
        </small>
      ) : null}
    </section>
  );
}

function buildProviderConfigurationSnippet(item: PackageDetail): string {
  return `terraform {
  required_providers {
    ${item.name} = {
      source  = "${item.namespace}/${item.name}"
      version = "${item.version}"
    }
  }
}

provider "${item.name}" {
  # Configuration options
}`;
}

function buildInstallSnippet(
  item: NonNullable<ReturnType<typeof usePackage>["data"]>,
  jfrogHostname: string,
): string {
  const jfrogBase = /^https?:\/\//i.test(jfrogHostname)
    ? jfrogHostname.replace(/\/$/, "")
    : `https://${jfrogHostname || "artifactory.internal"}`;
  const artifactUrl =
    item.artifactRepository && item.artifactPath
      ? `${jfrogBase}/artifactory/${item.artifactRepository}/${item.artifactPath}`
      : undefined;
  if (item.kind === "provider") {
    const source = `registry.terraform.io/${item.namespace}/${item.name}`;
    const filename =
      item.artifactPath?.split("/").at(-1) ??
      `terraform-provider-${item.name}_${item.version}_linux_amd64.zip`;
    const mirrorDirectory = `.terraform/providers/${source}`;
    const download = artifactUrl
      ? `mkdir -p "${mirrorDirectory}"\ncurl --fail --location --header "Authorization: Bearer $JFROG_ACCESS_TOKEN" "${artifactUrl}" --output "${mirrorDirectory}/${filename}"`
      : `# Resolve the approved archive in Artifactory before installing ${source}.`;
    const checksum = item.packageDigest?.replace(/^sha256:/, "");
    return `${download}${
      checksum
        ? `\necho "${checksum}  ${mirrorDirectory}/${filename}" | sha256sum --check`
        : ""
    }\n\n# Add this mirror to ~/.terraformrc\nprovider_installation {\n  filesystem_mirror {\n    path    = ".terraform/providers"\n    include = ["${source}"]\n  }\n}\n\nterraform {\n  required_providers {\n    ${item.name} = {\n      source  = "${source}"\n      version = "${item.version}"\n    }\n  }\n}`;
  }
  const source =
    artifactUrl ?? item.installSource ?? "ARTIFACTORY_URL_REQUIRED";
  return `module "${item.name.replaceAll("-", "_")}" {\n  # The immutable version is pinned in the Artifactory path.\n  source = "${source}"\n}`;
}
