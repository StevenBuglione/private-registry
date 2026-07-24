import { ArrowSquareOutIcon, CaretRightIcon } from "@phosphor-icons/react";
import { useState } from "react";
import { Link } from "react-router";
import { PackageIcon } from "../../components/PackageIcon";
import type {
  PackageDetail,
  PackageExample,
  PackageKind,
  PackageModuleChild,
} from "../../types";
import { hasText } from "../../utils";
import {
  formatCalendarDate,
  formatDownloadCount,
  type ModuleChildKind,
  moduleChildHref,
  moduleRootHref,
  namespaceHref,
  shortExternalUrl,
  sourceChildUrl,
} from "./model";

export function PackageHeaderActions({
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

export function ModuleFacts({
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
      <div>
        <span>Managed by:</span>
        <strong>{item.namespace}</strong>
      </div>
    </div>
  );
}

export function ProviderFacts({
  item,
  sourceRepository,
}: {
  item: PackageDetail;
  sourceRepository: string | undefined;
}) {
  const statistics = item.downloadStatistics;
  const category = providerCategory(item.name);
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
      {category === undefined ? null : (
        <span className="provider-category">{category}</span>
      )}
    </div>
  );
}

function providerCategory(name: string): string | undefined {
  return ["aws", "azurerm", "google"].includes(name.toLowerCase())
    ? "Public Cloud"
    : undefined;
}

export function ModuleChildHeader({
  item,
  childKind,
  childName,
  requestedVersion,
  sourceRepository,
}: {
  item: PackageDetail;
  childKind: ModuleChildKind;
  childName: string;
  requestedVersion: string;
  sourceRepository: string | undefined;
}) {
  const items = childKind === "submodule" ? item.submodules : item.examples;
  const variant = childKind === "submodule" ? "submodules" : "examples";
  const label = childKind === "submodule" ? "Submodule" : "Example";
  const rootHref = moduleRootHref(item, requestedVersion);
  const childSource = sourceChildUrl(
    sourceRepository,
    item.sourceTag,
    `${childKind === "submodule" ? "modules" : "examples"}/${childName}`,
  );
  const issueUrl = hasText(sourceRepository)
    ? `${sourceRepository.replace(/\/$/, "")}/issues`
    : undefined;
  return (
    <header className="package-source-header module-child-header source-container">
      <nav className="source-breadcrumbs" aria-label={`Module ${childKind}`}>
        <Link to="/browse/modules">Modules</Link>
        <span aria-hidden="true">/</span>
        <Link to={namespaceHref(item.namespace)}>{item.namespace}</Link>
        <span aria-hidden="true">/</span>
        <Link to={rootHref}>{item.name}</Link>
        <span aria-hidden="true">/</span>
        <Link to={rootHref}>v{item.version}</Link>
        <span aria-hidden="true">/</span>
        <span>{childName}</span>
      </nav>
      <h1>
        {label}: {childName}
      </h1>
      <p className="module-child-source">
        Source code:{" "}
        {hasText(childSource) ? (
          <a href={childSource} target="_blank" rel="noreferrer">
            {shortExternalUrl(childSource)}
          </a>
        ) : (
          <span>Unavailable</span>
        )}{" "}
        {hasText(issueUrl) ? (
          <>
            (
            <a href={issueUrl} target="_blank" rel="noreferrer">
              report an issue
            </a>
            )
          </>
        ) : null}
      </p>
      <div className="module-child-actions">
        <Link className="return-to-module" to={rootHref}>
          <CaretRightIcon aria-hidden="true" size={14} />
          Return to module {item.name}
        </Link>
        <ModuleChildMenu
          label={`Change ${childKind}`}
          items={items}
          item={item}
          version={requestedVersion}
          variant={variant}
        />
        {hasText(childSource) ? (
          <a
            className="view-source-button"
            href={childSource}
            target="_blank"
            rel="noreferrer"
          >
            <ArrowSquareOutIcon size={16} /> View Source
          </a>
        ) : null}
      </div>
    </header>
  );
}

export function ModuleChildMenu({
  label,
  items,
  item,
  version,
  variant,
}: {
  label: string;
  items: Array<PackageExample | PackageModuleChild>;
  item: PackageDetail;
  version: string;
  variant: "submodules" | "examples";
}) {
  const [open, setOpen] = useState(false);
  return (
    <div className="module-child-menu">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => {
          setOpen((value) => !value);
        }}
      >
        {label} <CaretRightIcon size={13} className={open ? "is-open" : ""} />
      </button>
      {open ? (
        <div className="module-child-menu-popover" role="menu">
          {items.map((child) => (
            <Link
              key={child.path}
              role="menuitem"
              to={moduleChildHref(item, version, variant, child.name)}
              onClick={() => {
                setOpen(false);
              }}
            >
              {child.name}
            </Link>
          ))}
        </div>
      ) : null}
    </div>
  );
}
