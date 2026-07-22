import {
  CaretRightIcon,
  CheckIcon,
  ClipboardIcon,
  FileTextIcon,
  ListIcon,
  ShieldCheckIcon,
} from "@phosphor-icons/react";
import { useState } from "react";
import ReactMarkdown from "react-markdown";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import rehypeSanitize from "rehype-sanitize";
import remarkGfm from "remark-gfm";
import { ApiError } from "../api";
import { ApprovalBadge } from "../components/Badges";
import { PackageCard } from "../components/PackageCard";
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
import type { PackageKind } from "../types";
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
  const detail = usePackage(identity);
  const documentation = usePackageDocumentation(
    identity,
    detail.data?.documentation,
  );
  const governance = usePackageGovernance(identity, detail.data?.governance);
  const related = useCatalogPage({
    kind: "module",
    provider: detail.data?.provider,
    apmId: selectedApmId,
    approval: "approved",
    limit: 4,
  });
  const defaultTab = kind === "provider" ? "overview" : "readme";
  const tab = searchParams.get("tab") ?? defaultTab;

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
    item.documentation ??
    `# ${item.name}\n\nDocumentation has not been published for this package version.`;
  const governanceData = governance.data ?? item.governance;
  const installSnippet = buildInstallSnippet(
    item,
    runtimeConfig().jfrogHostname,
  );
  const setTab = (value: string) => {
    const next = new URLSearchParams(searchParams);
    next.set("tab", value);
    setSearchParams(next, { replace: true });
  };
  const changeVersion = (version: string) =>
    navigate(packageHref({ ...item, version }));
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
          <label className="version-select">
            <span>Version</span>
            <select
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
        </div>
        <p className="package-description">{item.description}</p>
        <div className="package-facts">
          <span>
            Versions: <strong>{item.versions.length}</strong>
          </span>
          <span>
            Owner: <strong>{governanceData?.owner ?? item.owner}</strong>
          </span>
          <span>
            Lifecycle: <strong>{item.lifecycle}</strong>
          </span>
          <span>
            Updated: <strong>{formatRelativeDate(item.updatedAt)}</strong>
          </span>
          <span>
            Risk: <strong>{item.risk}</strong>
          </span>
        </div>
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
                onClick={() => setTab(value)}
              >
                {value}
              </button>
            ),
          )
        )}
      </nav>

      {showDocumentation ? (
        <ProviderDocumentation docs={docs} packageName={item.name} />
      ) : (
        <div className="package-content-surface">
          <div className="source-container package-overview-grid">
            <main>
              {kind === "provider" && tab === "overview" ? (
                <>
                  <div className="content-title-row">
                    <h2>Approved {item.name} modules</h2>
                    <Link
                      to={`/modules?provider=${encodeURIComponent(item.provider)}`}
                    >
                      View all modules <CaretRightIcon size={14} />
                    </Link>
                  </div>
                  <p>
                    Reusable packages for this provider that are available to
                    your current access context.
                  </p>
                  {related.data?.items.length ? (
                    <div className="related-module-grid">
                      {related.data.items.map((module) => (
                        <PackageCard
                          key={`${module.namespace}-${module.name}`}
                          item={module}
                        />
                      ))}
                    </div>
                  ) : (
                    <StatePanel kind="empty" />
                  )}
                </>
              ) : (
                <article className="documentation source-readme">
                  <ReactMarkdown
                    remarkPlugins={[remarkGfm]}
                    rehypePlugins={[rehypeSanitize]}
                  >
                    {docs}
                  </ReactMarkdown>
                </article>
              )}
            </main>
            <aside className="source-install-sidebar">
              <InstallPanel snippet={installSnippet} kind={kind} />
              <section className="source-governance-card">
                <h2>
                  <ShieldCheckIcon size={18} /> Governance
                </h2>
                <dl>
                  <div>
                    <dt>Owner</dt>
                    <dd>{governanceData?.owner ?? item.owner}</dd>
                  </div>
                  <div>
                    <dt>Support</dt>
                    <dd>{governanceData?.support ?? "Internal support"}</dd>
                  </div>
                  <div>
                    <dt>APM access</dt>
                    <dd>
                      {(governanceData?.apmIds.length
                        ? governanceData.apmIds
                        : item.apmIds
                      ).join(", ") ||
                        selectedApmId ||
                        "Administrator"}
                    </dd>
                  </div>
                </dl>
              </section>
            </aside>
          </div>
        </div>
      )}
    </div>
  );
}

function ProviderDocumentation({
  docs,
  packageName,
}: {
  docs: string;
  packageName: string;
}) {
  const [browseOpen, setBrowseOpen] = useState(false);
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
      <aside className={`provider-docs-nav ${browseOpen ? "is-open" : ""}`}>
        <strong>{packageName} documentation</strong>
        <input aria-label="Filter documentation" placeholder="Filter" />
        <a href="#overview" className="active">
          {packageName} provider
        </a>
        <a href="#guides">Guides</a>
        <a href="#resources">Resources</a>
        <a href="#data-sources">Data Sources</a>
        <a href="#functions">Functions</a>
      </aside>
      <article className="documentation" id="overview">
        <ReactMarkdown
          remarkPlugins={[remarkGfm]}
          rehypePlugins={[rehypeSanitize]}
        >
          {docs}
        </ReactMarkdown>
      </article>
      <aside className="on-this-page">
        <strong>
          <FileTextIcon size={15} /> On this page
        </strong>
        <a href="#overview">Overview</a>
        <a href="#configuration">Configuration</a>
        <a href="#examples">Examples</a>
        <a href="#arguments">Argument reference</a>
      </aside>
    </div>
  );
}

function InstallPanel({
  snippet,
  kind,
}: {
  snippet: string;
  kind: PackageKind;
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
        Copy this approved configuration into your project, then initialize your
        workspace.
      </p>
      <pre>
        <code>{snippet}</code>
      </pre>
      <button type="button" onClick={() => void copy()}>
        {copied ? <CheckIcon size={16} /> : <ClipboardIcon size={16} />}
        {copied ? "Copied" : "Copy"}
      </button>
    </section>
  );
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
