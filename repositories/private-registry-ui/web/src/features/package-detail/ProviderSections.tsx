import {
  ArrowSquareOutIcon,
  CardsIcon,
  CaretRightIcon,
  ClockIcon,
  DownloadSimpleIcon,
  FileTextIcon,
  LinkSimpleIcon,
  ListIcon,
  MagnifyingGlassIcon,
  WarningCircleIcon,
} from "@phosphor-icons/react";
import { useMemo, useState } from "react";
import { Link } from "react-router";
import { PackageIcon } from "../../components/PackageIcon";
import { StatePanel } from "../../components/StatePanel";
import type { PackageDetail, PackageSummary, PackageSymbol } from "../../types";
import { formatRelativeDate, hasText, packageHref } from "../../utils";
import { InstallPanel } from "./InstallPanel";
import { MarkdownDocument } from "./MarkdownDocument";
import { DownloadStatisticsCard } from "./ModuleSections";
import {
  displayProviderSymbolName,
  documentSectionKey,
  extractMarkdownHeadings,
  formatDownloadCount,
  providerDocumentGroups,
} from "./model";

export function ProviderOverview({
  item,
  modules,
  moduleTotal,
  modulesPending,
  snippet,
  sourceRepository,
}: {
  item: PackageDetail;
  modules: PackageSummary[];
  moduleTotal: number;
  modulesPending: boolean;
  snippet: string;
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
      </aside>
    </div>
  );
}

export function ProviderDocumentation({
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
