import {
  ArrowSquareOutIcon,
  CardsIcon,
  CaretRightIcon,
  CheckIcon,
  ClipboardIcon,
  ClockIcon,
  DownloadSimpleIcon,
  FileTextIcon,
  HandshakeIcon,
  InfoIcon,
  LinkSimpleIcon,
  ListIcon,
  MagnifyingGlassIcon,
  ShieldCheckIcon,
  WarningCircleIcon,
  WarningIcon,
} from "@phosphor-icons/react";
import {
  Children,
  isValidElement,
  type ReactNode,
  useMemo,
  useState,
} from "react";
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
import { runtimeConfig } from "../runtime-config";
import type {
  DownloadStatistics,
  GovernanceRecord,
  PackageDetail,
  PackageExample,
  PackageKind,
  PackageSummary,
  PackageSymbol,
} from "../types";
import { useRegistry } from "../use-registry";
import { formatRelativeDate, hasText, packageHref } from "../utils";

export function PackageDetailPage({ kind }: { kind: PackageKind }) {
  const params = useParams();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { selectedApmId } = useRegistry();
  const identity = {
    kind,
    namespace: params["namespace"] ?? "",
    name: params["name"] ?? "",
    target: kind === "module" ? params["target"] : undefined,
    version: params["version"],
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
    sort: "downloads",
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
          {...(notFound ? {} : { action: () => void detail.refetch() })}
        />
      </div>
    );
  }

  const item = detail.data;
  const docs =
    documentation.data ??
    (hasText(documentPath) ? undefined : item.documentation) ??
    `# ${item.name}\n\nDocumentation has not been published for this package version.`;
  const governanceData = governance.data ?? item.governance;
  const installSnippet = buildInstallSnippet(
    item,
    runtimeConfig().jfrogHostname,
  );
  const providerSnippet = buildProviderConfigurationSnippet(item);
  const sourceRepository = safeExternalUrl(
    item.sourceRepository ?? governanceData?.sourceRepository,
  );
  const setTab = (value: string) => {
    const next = new URLSearchParams(searchParams);
    next.set("tab", value);
    if (value !== "documentation") next.delete("doc");
    setSearchParams(next);
  };
  const selectDocument = (path?: string) => {
    const next = new URLSearchParams(searchParams);
    next.set("tab", "documentation");
    if (hasText(path)) next.set("doc", path);
    else next.delete("doc");
    setSearchParams(next);
  };
  const changeVersion = (version: string) => {
    const destination = packageHref({ ...item, version });
    const query = searchParams.toString();
    void navigate(query ? `${destination}?${query}` : destination);
  };
  const showDocumentation = kind === "provider" && tab === "documentation";

  return (
    <div className={`detail-page ${kind}-detail-page`}>
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
          <PackageIcon
            kind={kind}
            name={kind === "module" ? item.provider : item.name}
            size="large"
          />
          <div>
            <div className="package-name-line">
              <h1>{item.name}</h1>
              {kind === "module" && item.verified ? (
                <span className="registry-tier-badge">
                  <HandshakeIcon size={14} weight="fill" /> Partner
                </span>
              ) : (
                <ApprovalBadge
                  value={item.approval}
                  verified={item.verified}
                  {...(kind === "provider" && item.verified
                    ? { label: "Official" }
                    : {})}
                />
              )}
            </div>
            <span>
              {item.namespace}/{item.name}
              {hasText(item.target) ? `/${item.target}` : ""}
            </span>
          </div>
        </div>
        {kind === "provider" ? (
          <p className="package-description">{item.description}</p>
        ) : null}
        {kind === "module" ? (
          <ModuleFacts item={item} sourceRepository={sourceRepository} />
        ) : (
          <ProviderFacts item={item} sourceRepository={sourceRepository} />
        )}
        <PackageHeaderActions
          kind={kind}
          item={item}
          sourceRepository={sourceRepository}
          onVersionChange={changeVersion}
        />
        {kind === "module" && item.examples.length > 0 ? (
          <div className="module-examples-row">
            <ExamplesMenu
              examples={item.examples}
              sourceRepository={sourceRepository}
              sourceTag={item.sourceTag}
            />
          </div>
        ) : null}
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
              onClick={() => {
                setTab("overview");
              }}
            >
              Overview
            </button>
            <button
              className={tab === "documentation" ? "active" : ""}
              type="button"
              onClick={() => {
                setTab("documentation");
              }}
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
                    : `${capitalize(value)} (${String(moduleTabCount(item.symbols, value))})`
                }
                onClick={() => {
                  setTab(value);
                }}
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
              modulesPending={related.isPending}
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
                {kind === "module" ? (
                  <ModuleDownloadsCard
                    statistics={item.downloadStatistics}
                    statisticsByVersion={item.downloadStatisticsByVersion}
                  />
                ) : null}
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

function PackageHeaderActions({
  kind,
  item,
  sourceRepository,
  onVersionChange,
}: {
  kind: PackageKind;
  item: PackageDetail;
  sourceRepository: string | undefined;
  onVersionChange: (version: string) => void;
}) {
  return (
    <div className="package-header-actions">
      <label className="version-select">
        <span>Version</span>
        <select
          aria-label={`${kind === "provider" ? "Provider" : "Module"} version`}
          value={item.version}
          onChange={(event) => {
            onVersionChange(event.target.value);
          }}
        >
          {item.versions.map((version) => (
            <option key={version} value={version}>
              Version {version}
              {version === item.versions[0] ? " (latest)" : ""}
            </option>
          ))}
        </select>
      </label>
      {hasText(sourceRepository) ? (
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
  );
}

function ModuleFacts({
  item,
  sourceRepository,
}: {
  item: PackageDetail;
  sourceRepository: string | undefined;
}) {
  const statistics = item.downloadStatistics;
  return (
    <div className="module-package-facts" aria-label="Module metadata">
      <div>
        <span>Provider:</span>
        <strong className="package-provider-fact">
          <PackageIcon kind="provider" name={item.provider} size="small" />
          {item.provider}
        </strong>
      </div>
      <div title="Downloads served by this JFrog Artifactory mirror">
        <span>Downloads:</span>
        <strong>{formatDownloadCount(statistics?.allTime)}</strong>
      </div>
      <div title="Rolling history is available after a seven-day local baseline exists">
        <span>This week:</span>
        <strong>{formatDownloadCount(statistics?.week)}</strong>
      </div>
      <div>
        <span>Versions:</span>
        <strong>{item.versions.length}</strong>
      </div>
      {hasText(sourceRepository) ? (
        <div className="module-source-fact">
          <span>Source code:</span>
          <a href={sourceRepository} target="_blank" rel="noreferrer">
            {shortExternalUrl(sourceRepository)}
          </a>
        </div>
      ) : null}
      <div>
        <span>Published:</span>
        <strong>
          {formatCalendarDate(item.publishedAt ?? item.updatedAt)}
        </strong>
      </div>
      <div>
        <span>Published by:</span>
        <strong>{item.namespace}</strong>
      </div>
    </div>
  );
}

function ProviderFacts({
  item,
  sourceRepository,
}: {
  item: PackageDetail;
  sourceRepository: string | undefined;
}) {
  const statistics = item.downloadStatistics;
  return (
    <div className="provider-package-facts" aria-label="Provider metadata">
      <div className="provider-facts-row">
        <div title="Downloads served by this JFrog Artifactory mirror">
          <span>Downloads:</span>
          <strong>{formatDownloadCount(statistics?.allTime)}</strong>
        </div>
        <div title="Rolling history is available after a seven-day local baseline exists">
          <span>This week:</span>
          <strong>{formatDownloadCount(statistics?.week)}</strong>
        </div>
        <div>
          <span>Versions:</span>
          <strong>{item.versions.length}</strong>
        </div>
        {hasText(sourceRepository) ? (
          <div>
            <span>Source code:</span>
            <a href={sourceRepository} target="_blank" rel="noreferrer">
              {item.namespace}/{item.name}
            </a>
          </div>
        ) : null}
        <div>
          <span>Published:</span>
          <strong>
            {formatCalendarDate(item.publishedAt ?? item.updatedAt)}
          </strong>
        </div>
        <div>
          <span>Published by:</span>
          <strong>{item.namespace}</strong>
        </div>
      </div>
      <span className="package-category">{capitalize(item.lifecycle)}</span>
    </div>
  );
}

function ExamplesMenu({
  examples,
  sourceRepository,
  sourceTag,
}: {
  examples: PackageExample[];
  sourceRepository: string | undefined;
  sourceTag: string | undefined;
}) {
  const [open, setOpen] = useState(false);
  return (
    <div className="examples-menu">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => {
          setOpen((value) => !value);
        }}
      >
        Examples <CaretRightIcon size={13} className={open ? "is-open" : ""} />
      </button>
      {open ? (
        <div className="examples-menu-popover" role="menu">
          {examples.map((example) => {
            const url = sourceExampleUrl(
              sourceRepository,
              sourceTag,
              example.path,
            );
            return url === undefined ? (
              <span key={example.path} role="menuitem">
                {example.name}
              </span>
            ) : (
              <a
                key={example.path}
                role="menuitem"
                href={url}
                target="_blank"
                rel="noreferrer"
              >
                {example.name} <ArrowSquareOutIcon size={13} />
              </a>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}

function ModuleDownloadsCard({
  statistics,
  statisticsByVersion,
}: {
  statistics: DownloadStatistics | undefined;
  statisticsByVersion: Record<string, DownloadStatistics>;
}) {
  return (
    <DownloadStatisticsCard
      title="Module Downloads"
      statistics={statistics}
      statisticsByVersion={statisticsByVersion}
    />
  );
}

function DownloadStatisticsCard({
  title,
  statistics,
  statisticsByVersion,
}: {
  title: string;
  statistics: DownloadStatistics | undefined;
  statisticsByVersion: Record<string, DownloadStatistics>;
}) {
  const [statisticsVersion, setStatisticsVersion] = useState("all");
  const displayedStatistics =
    statisticsVersion === "all"
      ? statistics
      : statisticsByVersion[statisticsVersion];
  return (
    <section
      className="module-downloads-card"
      title="Download counts served by this Registry's JFrog Artifactory mirror"
    >
      <div className="module-downloads-header">
        <h2>
          <DownloadSimpleIcon size={15} /> {title}
        </h2>
        <select
          aria-label="Download statistics version"
          value={statisticsVersion}
          onChange={(event) => {
            setStatisticsVersion(event.target.value);
          }}
        >
          <option value="all">All versions</option>
          {Object.keys(statisticsByVersion).map((version) => (
            <option key={version} value={version}>
              Version {version}
            </option>
          ))}
        </select>
      </div>
      <dl>
        <div>
          <dt>Downloads this week</dt>
          <dd>{formatDownloadCount(displayedStatistics?.week)}</dd>
        </div>
        <div>
          <dt>Downloads this month</dt>
          <dd>{formatDownloadCount(displayedStatistics?.month)}</dd>
        </div>
        <div>
          <dt>Downloads this year</dt>
          <dd>{formatDownloadCount(displayedStatistics?.year)}</dd>
        </div>
        <div>
          <dt>Downloads over all time</dt>
          <dd>{formatDownloadCount(displayedStatistics?.allTime)}</dd>
        </div>
      </dl>
    </section>
  );
}

function formatDownloadCount(value: number | undefined): string {
  return value === undefined
    ? "—"
    : new Intl.NumberFormat("en-US").format(value);
}

function shortExternalUrl(value: string): string {
  try {
    const url = new URL(value);
    return `${url.hostname}${url.pathname}`.replace(/\/$/, "");
  } catch {
    return value;
  }
}

function sourceExampleUrl(
  sourceRepository: string | undefined,
  sourceTag: string | undefined,
  path: string,
): string | undefined {
  if (!hasText(sourceRepository) || !hasText(sourceTag)) return undefined;
  try {
    const url = new URL(sourceRepository);
    if (url.hostname !== "github.com") return undefined;
    url.pathname = `${url.pathname.replace(/\/$/, "")}/tree/${encodeURIComponent(sourceTag)}/${path
      .split("/")
      .map(encodeURIComponent)
      .join("/")}`;
    return url.toString();
  } catch {
    return undefined;
  }
}

function ProviderOverview({
  item,
  modules,
  moduleTotal,
  modulesPending,
  snippet,
  governance,
  selectedApmId,
  sourceRepository,
}: {
  item: PackageDetail;
  modules: PackageSummary[];
  moduleTotal: number;
  modulesPending: boolean;
  snippet: string;
  governance: GovernanceRecord | undefined;
  selectedApmId: string | undefined;
  sourceRepository: string | undefined;
}) {
  const sourceIssuesUrl = hasText(sourceRepository)
    ? `${sourceRepository.replace(/\/$/, "")}/issues`
    : undefined;
  return (
    <div className="source-container provider-overview-grid">
      <main>
        <div className="content-title-row provider-modules-heading">
          <h2>
            <CardsIcon size={18} /> Top downloaded {item.name} modules
          </h2>
          <Link to={`/modules?provider=${encodeURIComponent(item.provider)}`}>
            View all modules <CaretRightIcon size={14} />
          </Link>
        </div>
        <p className="provider-overview-intro">
          Modules are self-contained packages of Terraform configurations that
          are managed as a group.
        </p>
        {modulesPending ? (
          <div
            aria-label="Loading provider modules"
            className="detail-loading provider-modules-loading"
          />
        ) : modules.length > 0 ? (
          <>
            <p className="provider-module-count">
              Showing 1 - {modules.length} of{" "}
              {Math.max(moduleTotal, modules.length)} available modules
            </p>
            <div className="provider-module-list">
              {modules.map((module) => (
                <Link
                  className="provider-module-row"
                  key={`${module.namespace}-${module.name}-${module.target ?? "general"}`}
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
                      <span>
                        <ClockIcon size={14} />
                        {formatRelativeDate(module.updatedAt)}
                      </span>
                      <span title="Downloads served by this JFrog Artifactory mirror">
                        <DownloadSimpleIcon size={14} />
                        {formatDownloadCount(
                          module.downloadStatistics?.allTime,
                        )}
                      </span>
                    </small>
                  </div>
                </Link>
              ))}
            </div>
          </>
        ) : (
          <StatePanel kind="empty" />
        )}
        <section className="provider-helpful-links">
          <h2>
            <LinkSimpleIcon size={18} /> Helpful Links
          </h2>
          <nav aria-label="Provider links">
            {hasText(sourceRepository) ? (
              <a href={sourceRepository} target="_blank" rel="noreferrer">
                <ArrowSquareOutIcon size={16} /> Source Code
              </a>
            ) : null}
            <a
              href="https://www.terraform.io/docs/configuration/providers.html?utm_source=tf_registry&utm_content=sidebar"
              target="_blank"
              rel="noreferrer"
            >
              <ArrowSquareOutIcon size={16} /> Using providers
            </a>
            <a
              href="https://app.terraform.io/signup/account?utm_source=tf_registry&utm_content=sidebar"
              target="_blank"
              rel="noreferrer"
            >
              <ArrowSquareOutIcon size={16} /> Try HCP Terraform
            </a>
            <a
              href="https://learn.hashicorp.com/terraform?utm_source=tf_registry&utm_content=sidebar"
              target="_blank"
              rel="noreferrer"
            >
              <ArrowSquareOutIcon size={16} /> View tutorials
            </a>
            <a
              href="https://www.hashicorp.com/events?product=terraform&type=all&utm_source=tf_registry&utm_content=sidebar"
              target="_blank"
              rel="noreferrer"
            >
              <ArrowSquareOutIcon size={16} /> Register for a workshop
            </a>
            <a
              href="https://discuss.hashicorp.com/c/terraform-providers/31"
              target="_blank"
              rel="noreferrer"
            >
              <ArrowSquareOutIcon size={16} /> Post a forum question
            </a>
            {hasText(sourceIssuesUrl) ? (
              <a href={sourceIssuesUrl} target="_blank" rel="noreferrer">
                <WarningCircleIcon size={16} /> Report Issue
              </a>
            ) : null}
          </nav>
        </section>
      </main>
      <aside
        className="provider-overview-sidebar"
        aria-label="Provider details"
      >
        <DownloadStatisticsCard
          title="Provider Downloads"
          statistics={item.downloadStatistics}
          statisticsByVersion={item.downloadStatisticsByVersion}
        />
        <InstallPanel
          snippet={snippet}
          kind="provider"
          {...(hasText(item.artifactRepository)
            ? {
                artifactLabel: hasText(item.artifactPath)
                  ? `${item.artifactRepository}/${item.artifactPath}`
                  : item.artifactRepository,
              }
            : {})}
        />
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
  governance: GovernanceRecord | undefined;
  selectedApmId: string | undefined;
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
            {(governance?.apmIds.length ?? 0) > 0
              ? governance?.apmIds.join(", ")
              : item.apmIds.join(", ") ||
                (hasText(selectedApmId) ? selectedApmId : "Administrator")}
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
  selectedPath: string | undefined;
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
      group.sections.some((section) =>
        section.items.some((symbol) => symbol.path === selectedPath),
      ),
    );
    if (selectedGroup === undefined) return new Set();
    const selectedSection = selectedGroup.sections.find((section) =>
      section.items.some((symbol) => symbol.path === selectedPath),
    );
    return new Set([
      selectedGroup.label,
      ...(selectedSection !== undefined
        ? [documentSectionKey(selectedGroup.label, selectedSection.label)]
        : []),
    ]);
  });
  const headings = extractMarkdownHeadings(docs).filter(
    (heading) => heading.level > 1,
  );
  const matchingDocumentCount = groups.reduce(
    (count, group) =>
      count +
      group.sections.reduce(
        (sectionCount, section) => sectionCount + section.items.length,
        0,
      ),
    0,
  );
  const autoExpandedGroups = useMemo(() => {
    if (filter.trim()) {
      return new Set(
        groups.flatMap((group) => [
          group.label,
          ...group.sections
            .filter((section) => section.items.length > 0)
            .map((section) => documentSectionKey(group.label, section.label)),
        ]),
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
        onClick={() => {
          setBrowseOpen((value) => !value);
        }}
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
              onChange={(event) => {
                setFilter(event.target.value);
              }}
            />
          </label>
          <button
            type="button"
            className={!hasText(selectedPath) ? "active" : ""}
            aria-current={!hasText(selectedPath) ? "page" : undefined}
            onClick={() => {
              select();
            }}
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
                onClick={() => {
                  toggleGroup(group.label);
                }}
              >
                <CaretRightIcon
                  className={isGroupExpanded(group.label) ? "expanded" : ""}
                  size={13}
                />
                {group.label}
              </button>
            </h2>
            {isGroupExpanded(group.label)
              ? group.sections.map((section) => {
                  const sectionKey = documentSectionKey(
                    group.label,
                    section.label,
                  );
                  const flattened =
                    group.sections.length === 1 &&
                    section.label === group.label;
                  return (
                    <div className="docs-nav-section" key={sectionKey}>
                      {!flattened ? (
                        <button
                          type="button"
                          className="docs-section-toggle"
                          aria-expanded={isGroupExpanded(sectionKey)}
                          onClick={() => {
                            toggleGroup(sectionKey);
                          }}
                        >
                          <CaretRightIcon
                            className={
                              isGroupExpanded(sectionKey) ? "expanded" : ""
                            }
                            size={12}
                          />
                          {section.label}
                        </button>
                      ) : null}
                      {flattened || isGroupExpanded(sectionKey)
                        ? section.items.map((symbol) => (
                            <button
                              type="button"
                              key={`${symbol.kind}-${symbol.name}-${symbol.path}`}
                              className={
                                selectedPath === symbol.path ? "active" : ""
                              }
                              aria-current={
                                selectedPath === symbol.path
                                  ? "page"
                                  : undefined
                              }
                              title={symbol.description}
                              onClick={() => {
                                setExpandedGroups(
                                  new Set([group.label, sectionKey]),
                                );
                                select(symbol.path);
                              }}
                            >
                              {displayProviderSymbolName(packageName, symbol)}
                            </button>
                          ))
                        : null}
                    </div>
                  );
                })
              : null}
          </section>
        ))}
        {filter &&
        groups.every((group) =>
          group.sections.every((section) => section.items.length === 0),
        ) ? (
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
            key={`${String(heading.level)}-${heading.id}`}
            className={`heading-level-${String(heading.level)}`}
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
  const required = symbols.filter((symbol) => symbol.required === true);
  const optional = symbols.filter((symbol) => symbol.required !== true);
  return (
    <section className="module-symbol-panel">
      {required.length ? (
        <DefinitionSection
          title="Required Inputs"
          description="These variables must be set in the module block when using this module."
          symbols={required}
          showDefault={false}
        />
      ) : null}
      {optional.length ? (
        <DefinitionSection
          title="Optional Inputs"
          description="These variables have default values and don't have to be set to use this module. You may set these variables to override their default values."
          symbols={optional}
          showDefault
        />
      ) : null}
    </section>
  );
}

function OutputDefinitions({ symbols }: { symbols: PackageSymbol[] }) {
  return (
    <section className="module-symbol-panel" aria-label="Outputs">
      <DefinitionList symbols={symbols} showDefault={false} showType={false} />
    </section>
  );
}

function DefinitionSection({
  title,
  description,
  symbols,
  showDefault,
  showType = true,
}: {
  title: string;
  description: string;
  symbols: PackageSymbol[];
  showDefault: boolean;
  showType?: boolean;
}) {
  return (
    <section className="module-definition-section" aria-label={title}>
      <h2>{title}</h2>
      <p>
        {title === "Required Inputs" ? (
          <>
            These variables must be set in the <code>module</code> block when
            using this module.
          </>
        ) : (
          description
        )}
      </p>
      <DefinitionList
        symbols={symbols}
        showDefault={showDefault}
        showType={showType}
      />
    </section>
  );
}

function DefinitionList({
  symbols,
  showDefault,
  showType,
}: {
  symbols: PackageSymbol[];
  showDefault: boolean;
  showType: boolean;
}) {
  return (
    <dl className="module-definition-list">
      {symbols.map((symbol) => (
        <div key={`${symbol.kind}-${symbol.name}-${symbol.path}`}>
          <dt>
            <strong>{symbol.name}</strong>
            <DefinitionCopyButton value={symbol.name} />
            {showType ? (
              <code className="definition-type">
                {symbol.type ?? "Unknown"}
              </code>
            ) : null}
            {symbol.sensitive === true ? (
              <span className="definition-sensitive">Sensitive</span>
            ) : null}
          </dt>
          <dd>
            <p>
              <em>Description:</em> {cleanSymbolDescription(symbol.description)}
            </p>
            {showDefault ? (
              <p>
                <em>Default:</em>{" "}
                <code>{formatDefaultValue(symbol.defaultValue)}</code>
              </p>
            ) : null}
          </dd>
        </div>
      ))}
    </dl>
  );
}

function DefinitionCopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    window.setTimeout(() => {
      setCopied(false);
    }, 1400);
  };
  return (
    <button
      className="definition-copy"
      type="button"
      aria-label={`Copy ${value}`}
      title={copied ? "Copied" : `Copy ${value}`}
      onClick={() => void copy()}
    >
      {copied ? <CheckIcon size={13} /> : <ClipboardIcon size={13} />}
    </button>
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
                  : cleanSymbolDescription(symbol.description)}
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
  if (hasText(symbol.type)) return capitalize(symbol.type);
  const kind = normalizeSymbolKind(symbol.kind);
  if (kind.includes("provider")) return "Provider";
  if (kind.includes("module")) return "Module";
  return "Dependency";
}

function resourceProvider(symbol: PackageSymbol): string {
  if (hasText(symbol.provider)) return symbol.provider;
  const resourceType = symbol.type ?? symbol.name.split(".")[0] ?? symbol.name;
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
    window.setTimeout(() => {
      setCopied(false);
    }, 1800);
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
      if (isValidElement<{ children?: ReactNode }>(child)) {
        return reactNodeText(child.props.children);
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
    resources: ["resource"],
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
  const filtered = symbols.filter(matches);
  const guides = filtered.filter((symbol) =>
    ["guide", "document", "overview"].includes(
      normalizeSymbolKind(symbol.kind),
    ),
  );
  const functions = filtered.filter(
    (symbol) => normalizeSymbolKind(symbol.kind) === "function",
  );
  const categorySymbols = filtered.filter((symbol) =>
    ["resource", "data_source", "datasource", "list_resource"].includes(
      normalizeSymbolKind(symbol.kind),
    ),
  );
  const categories = new Map<string, PackageSymbol[]>();
  for (const symbol of categorySymbols) {
    const category = providerServiceCategory(symbol.name);
    categories.set(category, [...(categories.get(category) ?? []), symbol]);
  }

  const standalone = [
    { label: "Guides", items: guides },
    { label: "Functions", items: functions },
  ]
    .filter((group) => group.items.length > 0)
    .map((group) => ({
      label: group.label,
      sections: [{ label: group.label, items: group.items }],
    }));
  const services = [...categories.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([label, items]) => ({
      label,
      sections: [
        {
          label: "Resources",
          items: items.filter(
            (symbol) => normalizeSymbolKind(symbol.kind) === "resource",
          ),
        },
        {
          label: "Data Sources",
          items: items.filter((symbol) =>
            ["data_source", "datasource"].includes(
              normalizeSymbolKind(symbol.kind),
            ),
          ),
        },
        {
          label: "List Resources",
          items: items.filter(
            (symbol) => normalizeSymbolKind(symbol.kind) === "list_resource",
          ),
        },
      ].filter((section) => section.items.length > 0),
    }));
  return [...standalone, ...services];
}

function documentSectionKey(group: string, section: string) {
  return `${group}::${section}`;
}

function providerServiceCategory(name: string): string {
  const normalized = name
    .toLowerCase()
    .replace(/^azurerm_/, "")
    .replaceAll("-", "_");
  const categories: Array<[RegExp, string]> = [
    [/^(resource_group|subscription|resource_provider)/, "Base"],
    [/^(aad_b2c|aadb2c)/, "AAD B2C"],
    [/^(api_management)/, "API Management"],
    [/^(active_directory_domain)/, "Active Directory Domain Services"],
    [/^(advisor)/, "Advisor"],
    [/^(analysis_services)/, "Analysis Services"],
    [/^(app_configuration)/, "App Configuration"],
    [
      /^(app_service|function_app|service_plan|static_web_app)/,
      "App Service (Web Apps)",
    ],
    [/^(application_insights)/, "Application Insights"],
    [/^(arc_|kubernetes_flux)/, "ArcKubernetes"],
    [/^(authorization|role_|pim_|lighthouse)/, "Authorization"],
    [/^(automation)/, "Automation"],
    [/^(batch)/, "Batch"],
    [/^(billing)/, "Billing"],
    [/^(bot_)/, "Bot"],
    [/^(cdn_|frontdoor)/, "CDN"],
    [/^(chaos_)/, "Chaos Studio"],
    [/^(cognitive_|ai_services)/, "Cognitive Services"],
    [/^(communication_)/, "Communication"],
    [
      /^(linux_virtual_machine|windows_virtual_machine|virtual_machine|managed_disk|snapshot|image|gallery_)/,
      "Compute",
    ],
    [/^(container_|kubernetes_|log_analytics_solution)/, "Container"],
    [/^(cosmosdb_)/, "CosmosDB (DocumentDB)"],
    [/^(cost_management_)/, "Cost Management"],
    [/^(custom_provider)/, "Custom Providers"],
    [/^(dashboard)/, "Dashboard"],
    [/^(data_explorer|kusto_)/, "Data Explorer"],
    [/^(data_factory)/, "Data Factory"],
    [/^(data_share)/, "Data Share"],
    [/^(database_migration)/, "Database Migration"],
    [/^(mssql_|mysql_|postgresql_|mariadb_)/, "Database"],
    [/^(databricks_)/, "Databricks"],
    [/^(desktop_virtualization)/, "Desktop Virtualization"],
    [/^(dev_center|dev_test)/, "Dev Center"],
    [/^(digital_twins)/, "Digital Twins"],
    [/^(dns_|private_dns)/, "DNS"],
    [/^(eventgrid_|eventhub_|servicebus_|relay_)/, "Messaging"],
    [/^(healthcare_)/, "Healthcare"],
    [/^(iot_|iothub_)/, "IoT Hub"],
    [/^(key_vault)/, "Key Vault"],
    [/^(load_test)/, "Load Test"],
    [/^(log_analytics)/, "Log Analytics"],
    [/^(logic_app)/, "Logic App"],
    [/^(machine_learning)/, "Machine Learning"],
    [/^(maintenance_)/, "Maintenance"],
    [/^(management_group|management_lock)/, "Management"],
    [/^(maps_)/, "Maps"],
    [/^(monitor_|monitoring_|action_group)/, "Monitor"],
    [/^(netapp_)/, "NetApp"],
    [
      /^(virtual_network|subnet|network_|public_ip|private_endpoint|application_gateway|load_balancer|firewall|express_route|route_|traffic_manager|nat_gateway|bastion_)/,
      "Network",
    ],
    [/^(policy_)/, "Policy"],
    [/^(portal_)/, "Portal"],
    [/^(powerbi_)/, "PowerBI"],
    [/^(purview_)/, "Purview"],
    [/^(recovery_services|backup_)/, "Recovery Services"],
    [/^(redis_)/, "Redis"],
    [/^(search_)/, "Search"],
    [/^(security_center|sentinel_)/, "Security Center"],
    [/^(service_fabric)/, "Service Fabric"],
    [/^(spring_cloud)/, "Spring Cloud"],
    [/^(storage_|storageaccount)/, "Storage"],
    [/^(stream_analytics)/, "Stream Analytics"],
    [/^(synapse_)/, "Synapse"],
    [/^(template_|resource_deployment)/, "Template"],
  ];
  return (
    categories.find(([pattern]) => pattern.test(normalized))?.[1] ?? "Other"
  );
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
  if (value === null) return "null";
  if (
    typeof value === "number" ||
    typeof value === "boolean" ||
    typeof value === "bigint"
  ) {
    return String(value);
  }
  try {
    return JSON.stringify(value);
  } catch {
    return `Unserializable ${typeof value}`;
  }
}

function cleanSymbolDescription(value?: string): string {
  const description = value?.trim();
  if (
    !hasText(description) ||
    /^<<[A-Z_]+$/.test(description) ||
    /^(?:optional|list|map|set|object)\s*\(/.test(description)
  ) {
    return "No description published.";
  }
  return description;
}

function extractMarkdownHeadings(markdown: string) {
  return markdown
    .split(/\r?\n/)
    .map((line) => /^(#{1,3})\s+(.+?)\s*#*\s*$/.exec(line))
    .filter((match): match is RegExpExecArray => match !== null)
    .map((match) => {
      const markers = match[1] ?? "";
      const title = (match[2] ?? "").replaceAll(/[*_`[\]]/g, "").trim();
      return { level: markers.length, title, id: slugText(title) };
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
    timeZone: "UTC",
  }).format(date);
}

function safeExternalUrl(value?: string): string | undefined {
  if (!hasText(value)) return undefined;
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
    window.setTimeout(() => {
      setCopied(false);
    }, 1800);
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
          <>
            Copy and paste into your Terraform configuration, insert the
            variables, and run <code>terraform init</code>:
          </>
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
      {hasText(artifactLabel) ? (
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
    hasText(item.artifactRepository) && hasText(item.artifactPath)
      ? `${jfrogBase}/artifactory/${item.artifactRepository}/${item.artifactPath}`
      : undefined;
  if (item.kind === "provider") {
    const source = `registry.terraform.io/${item.namespace}/${item.name}`;
    const filename =
      item.artifactPath?.split("/").at(-1) ??
      `terraform-provider-${item.name}_${item.version}_linux_amd64.zip`;
    const mirrorDirectory = `.terraform/providers/${source}`;
    const download = hasText(artifactUrl)
      ? `mkdir -p "${mirrorDirectory}"\ncurl --fail --location --header "Authorization: Bearer $JFROG_ACCESS_TOKEN" "${artifactUrl}" --output "${mirrorDirectory}/${filename}"`
      : `# Resolve the approved archive in Artifactory before installing ${source}.`;
    const checksum = item.packageDigest?.replace(/^sha256:/, "");
    return `${download}${
      hasText(checksum)
        ? `\necho "${checksum}  ${mirrorDirectory}/${filename}" | sha256sum --check`
        : ""
    }\n\n# Add this mirror to ~/.terraformrc\nprovider_installation {\n  filesystem_mirror {\n    path    = ".terraform/providers"\n    include = ["${source}"]\n  }\n}\n\nterraform {\n  required_providers {\n    ${item.name} = {\n      source  = "${source}"\n      version = "${item.version}"\n    }\n  }\n}`;
  }
  const source =
    artifactUrl ?? item.installSource ?? "ARTIFACTORY_URL_REQUIRED";
  return `module "${item.name.replaceAll("-", "_")}" {\n  # The immutable version is pinned in the Artifactory path.\n  source = "${source}"\n}`;
}
