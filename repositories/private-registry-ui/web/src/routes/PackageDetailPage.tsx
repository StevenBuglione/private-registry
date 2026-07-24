import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import { ApiError } from "../api/client";
import { VerificationBadge } from "../components/Badges";
import { PackageIcon } from "../components/PackageIcon";
import { StatePanel } from "../components/StatePanel";
import {
  buildInstallSnippet,
  buildModuleChildInstallSnippet,
  buildProviderConfigurationSnippet,
  capitalize,
  hasRootModuleConfiguration,
  InstallPanel,
  MarkdownDocument,
  ModuleChildHeader,
  type ModuleChildKind,
  ModuleChildMenu,
  ModuleDownloadsCard,
  ModuleFacts,
  ModuleRootConfigurationNotice,
  ModuleTabContent,
  moduleChildHref,
  moduleRootHref,
  moduleTabCount,
  namespaceHref,
  PackageHeaderActions,
  ProviderDocumentation,
  ProviderFacts,
  ProviderOverview,
  providerLatestHref,
  safeExternalUrl,
  symbolsForModuleView,
} from "../features/package-detail";
import {
  useCatalogPage,
  usePackage,
  usePackageDocumentation,
} from "../hooks/catalog";
import type { PackageKind } from "../types";
import { hasText, packageHref } from "../utils";

export function PackageDetailPage({
  kind,
  moduleChildKind,
}: {
  kind: PackageKind;
  moduleChildKind?: ModuleChildKind;
}) {
  const params = useParams();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const identity = {
    kind,
    namespace: params["namespace"] ?? "",
    name: params["name"] ?? "",
    target: kind === "module" ? params["target"] : undefined,
    version: params["version"],
  };
  const defaultTab = kind === "provider" ? "overview" : "readme";
  const tab = searchParams.get("tab") ?? defaultTab;
  const documentPath =
    kind === "provider" && tab === "documentation"
      ? (searchParams.get("doc") ?? undefined)
      : undefined;
  const moduleChildName = params["moduleChild"];
  const moduleChildDocumentPath =
    kind === "module" &&
    moduleChildKind !== undefined &&
    hasText(moduleChildName)
      ? `${moduleChildKind === "submodule" ? "modules" : "examples"}/${moduleChildName}/README.md`
      : undefined;
  const detail = usePackage(identity);
  const documentation = usePackageDocumentation(
    identity,
    moduleChildDocumentPath === undefined
      ? detail.data?.documentation
      : undefined,
    documentPath ?? moduleChildDocumentPath,
  );
  const related = useCatalogPage({
    kind: "module",
    provider: detail.data?.provider,
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
  const moduleViewSymbols =
    kind === "module"
      ? symbolsForModuleView(item.symbols, moduleChildKind, moduleChildName)
      : item.symbols;
  const docs =
    documentation.data ??
    (hasText(documentPath) || hasText(moduleChildDocumentPath)
      ? undefined
      : item.documentation) ??
    `# ${item.name}\n\nDocumentation has not been published for this package version.`;
  const installSnippet =
    moduleChildKind === undefined || !hasText(moduleChildName)
      ? buildInstallSnippet(item)
      : buildModuleChildInstallSnippet(item, moduleChildKind, moduleChildName);
  const providerSnippet = buildProviderConfigurationSnippet(item);
  const sourceRepository = safeExternalUrl(item.sourceRepository);
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
    const destination =
      moduleChildKind === undefined || !hasText(moduleChildName)
        ? packageHref({ ...item, version })
        : moduleChildHref(
            item,
            version,
            moduleChildKind === "submodule" ? "submodules" : "examples",
            moduleChildName,
          );
    const query = searchParams.toString();
    void navigate(query ? `${destination}?${query}` : destination);
  };
  const showDocumentation = kind === "provider" && tab === "documentation";

  return (
    <div
      className={`detail-page ${kind}-detail-page${
        moduleChildKind === undefined ? "" : " module-child-detail-page"
      }`}
    >
      {kind === "module" &&
      moduleChildKind !== undefined &&
      hasText(moduleChildName) ? (
        <ModuleChildHeader
          item={item}
          childKind={moduleChildKind}
          childName={moduleChildName}
          requestedVersion={params["version"] ?? item.version}
          sourceRepository={sourceRepository}
        />
      ) : (
        <header className="package-source-header source-container">
          <nav className="source-breadcrumbs" aria-label="Breadcrumb">
            <Link
              to={kind === "provider" ? "/browse/providers" : "/browse/modules"}
            >
              {kind === "provider" ? "Providers" : "Modules"}
            </Link>
            <span aria-hidden="true">/</span>
            <Link to={namespaceHref(item.namespace)}>{item.namespace}</Link>
            <span aria-hidden="true">/</span>
            <Link
              to={
                kind === "provider"
                  ? providerLatestHref(item)
                  : moduleRootHref(item, params["version"] ?? item.version)
              }
            >
              {item.name}
            </Link>
            <span aria-hidden="true">/</span>
            <span>v{item.version}</span>
          </nav>
          <div className="package-title-row">
            <PackageIcon
              kind={kind}
              name={kind === "module" ? item.provider : item.name}
              {...(kind === "module" ? { namespace: item.namespace } : {})}
              size="large"
            />
            <div>
              <div className="package-name-line">
                <h1>{item.name}</h1>
                {item.registryTier === "official" ? (
                  <VerificationBadge label="Official" />
                ) : item.registryTier === "partner" ||
                  item.registryTier === "partner-premier" ? (
                  <VerificationBadge
                    label={
                      item.registryTier === "partner-premier"
                        ? "Partner Premier"
                        : "Partner"
                    }
                    tone="partner"
                  />
                ) : null}
              </div>
              {kind === "provider" ? (
                <span>
                  {item.namespace}/{item.name}
                </span>
              ) : null}
            </div>
          </div>
          {kind === "module" ? (
            <span className="module-package-address">
              {item.namespace}/{item.name}
              {hasText(item.target) ? `/${item.target}` : ""}
            </span>
          ) : null}
          <p className="package-description">{item.description}</p>
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
          {kind === "module" &&
          (item.submodules.length > 0 || item.examples.length > 0) ? (
            <div className="module-child-menus">
              {item.submodules.length > 0 ? (
                <ModuleChildMenu
                  label="Submodules"
                  items={item.submodules}
                  item={item}
                  version={params["version"] ?? item.version}
                  variant="submodules"
                />
              ) : null}
              {item.examples.length > 0 ? (
                <ModuleChildMenu
                  label="Examples"
                  items={item.examples}
                  item={item}
                  version={params["version"] ?? item.version}
                  variant="examples"
                />
              ) : null}
            </div>
          ) : null}
        </header>
      )}

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
          (moduleChildKind === "example"
            ? ["readme", "inputs", "outputs"]
            : ["readme", "inputs", "outputs", "dependencies", "resources"]
          ).map((value) => (
            <button
              key={value}
              className={tab === value ? "active" : ""}
              type="button"
              aria-label={
                value === "readme"
                  ? "Readme"
                  : `${capitalize(value)} (${String(moduleTabCount(moduleViewSymbols, value))})`
              }
              onClick={() => {
                setTab(value);
              }}
            >
              {capitalize(value)}
              {value !== "readme" ? (
                <span className="tab-count">
                  {moduleTabCount(moduleViewSymbols, value)}
                </span>
              ) : null}
            </button>
          ))
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
              sourceRepository={sourceRepository}
            />
          ) : (
            <div className="source-container package-overview-grid module-overview-grid">
              <main>
                {kind === "module" ? (
                  <>
                    {moduleChildKind === undefined &&
                    !hasRootModuleConfiguration(item.symbols) &&
                    item.submodules.length > 0 ? (
                      <ModuleRootConfigurationNotice
                        version={item.version}
                        submoduleCount={item.submodules.length}
                      />
                    ) : null}
                    <ModuleTabContent
                      tab={tab}
                      docs={docs}
                      symbols={moduleViewSymbols}
                    />
                  </>
                ) : (
                  <MarkdownDocument docs={docs} className="source-readme" />
                )}
              </main>
              <aside
                className="source-install-sidebar"
                aria-label="Installation"
              >
                {kind === "module" && moduleChildKind === undefined ? (
                  <ModuleDownloadsCard
                    statistics={item.downloadStatistics}
                    statisticsByVersion={item.downloadStatisticsByVersion}
                  />
                ) : null}
                <InstallPanel snippet={installSnippet} kind={kind} />
              </aside>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
