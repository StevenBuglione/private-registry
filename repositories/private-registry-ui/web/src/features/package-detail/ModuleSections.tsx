import {
  CheckIcon,
  ClipboardIcon,
  DownloadSimpleIcon,
  WarningIcon,
} from "@phosphor-icons/react";
import { useState } from "react";
import type { DownloadStatistics, PackageSymbol } from "../../types";
import { MarkdownDocument } from "./MarkdownDocument";
import {
  capitalize,
  cleanSymbolDescription,
  dependencyKind,
  formatDefaultValue,
  formatDownloadCount,
  symbolsForModuleTab,
} from "./model";

export function ModuleRootConfigurationNotice({
  version,
  submoduleCount,
}: {
  version: string;
  submoduleCount: number;
}) {
  return (
    <aside className="module-root-notice" role="alert">
      <WarningIcon aria-hidden="true" size={18} />
      <div>
        <strong>
          This module version ({version}) has no root configuration.
        </strong>
        <p>
          A module with no root configuration cannot be used directly. Use the
          submodules dropdown above to view the {submoduleCount} submodules
          defined within this module.
        </p>
      </div>
    </aside>
  );
}

export function ModuleDownloadsCard({
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

export function DownloadStatisticsCard({
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
          <dd
            className={displayedStatistics?.week === undefined ? "empty" : ""}
          >
            {formatDownloadCount(displayedStatistics?.week)}
          </dd>
        </div>
        <div>
          <dt>Downloads this month</dt>
          <dd
            className={displayedStatistics?.month === undefined ? "empty" : ""}
          >
            {formatDownloadCount(displayedStatistics?.month)}
          </dd>
        </div>
        <div>
          <dt>Downloads this year</dt>
          <dd
            className={displayedStatistics?.year === undefined ? "empty" : ""}
          >
            {formatDownloadCount(displayedStatistics?.year)}
          </dd>
        </div>
        <div>
          <dt>Downloads over all time</dt>
          <dd
            className={
              displayedStatistics?.allTime === undefined ? "empty" : ""
            }
          >
            {formatDownloadCount(displayedStatistics?.allTime)}
          </dd>
        </div>
      </dl>
    </section>
  );
}

export function ModuleTabContent({
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
  if (tab === "dependencies") return <DependencyDefinitions symbols={items} />;
  if (tab === "resources") return <ResourceDefinitions symbols={items} />;
  return null;
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

function DependencyDefinitions({ symbols }: { symbols: PackageSymbol[] }) {
  const moduleDependencies = symbols.filter(
    (symbol) => dependencyKind(symbol) === "Module",
  );
  const providerDependencies = symbols.filter(
    (symbol) => dependencyKind(symbol) === "Provider",
  );
  return (
    <section
      className="module-symbol-panel module-registry-sections"
      aria-label="Dependencies"
    >
      <section className="module-registry-section">
        <h2>Dependencies</h2>
        <p>
          Dependencies are external modules that this module references. A
          module is considered external if it isn&apos;t within the same
          repository.
        </p>
        {moduleDependencies.length === 0 ? (
          <p>
            <strong>This module has no external module dependencies.</strong>
          </p>
        ) : (
          <DependencyList symbols={moduleDependencies} />
        )}
      </section>
      <section className="module-registry-section">
        <h2>Provider Dependencies</h2>
        <p>
          Providers are Terraform plugins that will be automatically installed
          during <code>terraform init</code> if available on the Terraform
          Registry.
        </p>
        {providerDependencies.length === 0 ? (
          <p>
            <strong>This module has no provider dependencies.</strong>
          </p>
        ) : (
          <DependencyList symbols={providerDependencies} />
        )}
      </section>
    </section>
  );
}

function DependencyList({ symbols }: { symbols: PackageSymbol[] }) {
  return (
    <ul className="module-registry-list">
      {symbols.map((symbol) => {
        const source = symbol.source ?? symbol.description ?? symbol.name;
        const displayName =
          symbol.name.split("/").filter(Boolean).at(-1) ?? symbol.name;
        return (
          <li key={`${symbol.kind}-${symbol.name}-${symbol.path}`}>
            {displayName} ({source})
            {symbol.defaultValue === undefined ? null : (
              <>
                {" "}
                <code>{formatDefaultValue(symbol.defaultValue)}</code>
              </>
            )}
          </li>
        );
      })}
    </ul>
  );
}

function ResourceDefinitions({ symbols }: { symbols: PackageSymbol[] }) {
  return (
    <section
      className="module-symbol-panel module-registry-sections"
      aria-label="Resources"
    >
      <section className="module-registry-section">
        <h2>Resources</h2>
        <p>
          This is the list of resources that the module <em>may</em> create. The
          module can create zero or more of each of these resources depending on
          the <code>count</code> value. The count value is determined at
          runtime. The goal of this page is to present the types of resources
          that may be created.
        </p>
        <p>
          This list contains all the resources this plus any submodules may
          create. When using this module, it may create fewer resources if you
          use a submodule.
        </p>
        <p>
          This module defines{" "}
          <strong>
            {symbols.length} {symbols.length === 1 ? "resource" : "resources"}
          </strong>
          .
        </p>
        <ul className="module-registry-list module-resource-list">
          {symbols.map((symbol) => (
            <li key={`${symbol.kind}-${symbol.name}-${symbol.path}`}>
              <code>{symbol.name}</code>
            </li>
          ))}
        </ul>
      </section>
    </section>
  );
}
