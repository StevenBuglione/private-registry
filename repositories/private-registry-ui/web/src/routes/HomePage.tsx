import {
  ArrowRightIcon,
  CubeIcon,
  GlobeSimpleIcon,
  InfoIcon,
  PackageIcon,
} from "@phosphor-icons/react";
import { Link } from "react-router";
import { PackageCard } from "../components/PackageCard";
import { RegistryMark } from "../components/RegistryMark";
import { StatePanel } from "../components/StatePanel";
import { useCatalogPage, useHomepageSettings } from "../hooks";
import type { HomepageSettings } from "../types";

export function HomePage() {
  const settings = useHomepageSettings();
  const providers = useCatalogPage({
    kind: "provider",
    sort: "name",
    limit: 50,
  });
  const modules = useCatalogPage({
    kind: "module",
    sort: "name",
    limit: 100,
  });
  const providerCount = providers.data?.total ?? 0;
  const moduleCount = modules.data?.total ?? 0;
  const catalogError = providers.isError || modules.isError;
  const homepageSettings = settings.data ?? DEFAULT_HOMEPAGE_SETTINGS;
  const featured = featuredPackages(
    providers.data?.items ?? [],
    homepageSettings.featuredProviderIds,
  );
  const featuredModules = featuredPackages(
    modules.data?.items ?? [],
    homepageSettings.featuredModuleIds,
  );

  return (
    <div className="home-page">
      {homepageSettings.notificationEnabled ? (
        <section className="home-announcement">
          <div className="source-container">
            <InfoIcon size={20} weight="regular" />
            <div>
              <strong>{homepageSettings.notificationTitle}</strong>
              <p>
                {homepageSettings.notificationMessage}
                {homepageSettings.notificationLinkLabel !== undefined &&
                homepageSettings.notificationLinkUrl !== undefined ? (
                  <NotificationLink
                    label={homepageSettings.notificationLinkLabel}
                    href={homepageSettings.notificationLinkUrl}
                  />
                ) : null}
              </p>
            </div>
          </div>
        </section>
      ) : null}

      <section className="registry-hero source-container">
        <RegistryMark />
        <h1>Registry</h1>
        <p>
          Discover Terraform providers and reusable modules for building secure,
          reliable infrastructure.
        </p>
        <div className="hero-actions">
          <Link to="/providers">
            <PackageIcon size={19} /> Browse Providers
          </Link>
          <Link to="/modules">
            <CubeIcon size={19} /> Browse Modules
          </Link>
        </div>
        <div className="hero-counts">
          <strong>{providerCount}</strong> providers,{" "}
          <strong>{moduleCount}</strong> modules available to you
        </div>
      </section>

      {catalogError ? (
        <div className="source-container">
          <StatePanel
            kind="api-error"
            action={() => {
              window.location.reload();
            }}
          />
        </div>
      ) : (
        <div className="home-catalog source-container">
          <CatalogSection
            eyebrow="Featured providers"
            description="Popular infrastructure plugins available in your Registry."
            href="/providers"
            loading={providers.isPending}
            items={featured}
            variant="providers"
          />
          <CatalogSection
            eyebrow="Featured modules"
            description="Reusable Terraform configurations selected for your teams."
            href="/modules"
            loading={modules.isPending}
            items={featuredModules}
            variant="modules"
          />
          <HowTerraformWorks />
        </div>
      )}
    </div>
  );
}

const DEFAULT_FEATURED_PROVIDER_IDS = [
  "provider/hashicorp/google",
  "provider/hashicorp/azurerm",
  "provider/hashicorp/aws",
  "provider/hashicorp/kubernetes",
  "provider/hashicorp/helm",
  "provider/datadog/datadog",
];

const DEFAULT_FEATURED_MODULE_IDS = [
  "module/terraform-module/release/helm",
  "module/terraform-aws-modules/iam/aws",
  "module/terraform-google-modules/project-factory/google",
  "module/terraform-google-modules/network/google",
  "module/terraform-google-modules/kubernetes-engine/google",
  "module/Azure/avm-res-web-site/azurerm",
];

const DEFAULT_HOMEPAGE_SETTINGS: HomepageSettings = {
  notificationEnabled: true,
  notificationTitle: "Your private Registry is ready",
  notificationMessage:
    "Browse Terraform providers and modules available to your account.",
  featuredProviderIds: DEFAULT_FEATURED_PROVIDER_IDS,
  featuredModuleIds: DEFAULT_FEATURED_MODULE_IDS,
  updatedAt: "",
};

function featuredPackages(
  items: Parameters<typeof PackageCard>[0]["item"][],
  selectedIds: string[],
) {
  return [...items]
    .sort((left, right) => {
      const leftRank = selectedIds.indexOf(packageId(left));
      const rightRank = selectedIds.indexOf(packageId(right));
      return (
        (leftRank < 0 ? Number.MAX_SAFE_INTEGER : leftRank) -
          (rightRank < 0 ? Number.MAX_SAFE_INTEGER : rightRank) ||
        left.name.localeCompare(right.name)
      );
    })
    .filter((item) => selectedIds.includes(packageId(item)))
    .slice(0, 6);
}

function packageId(item: Parameters<typeof PackageCard>[0]["item"]) {
  return [
    item.kind,
    item.namespace,
    item.name,
    ...(item.target === undefined ? [] : [item.target]),
  ].join("/");
}

function NotificationLink({ label, href }: { label: string; href: string }) {
  return href.startsWith("/") ? (
    <Link to={href}>
      {label} <ArrowRightIcon size={14} />
    </Link>
  ) : (
    <a href={href} target="_blank" rel="noreferrer">
      {label} <ArrowRightIcon size={14} />
    </a>
  );
}

function HowTerraformWorks() {
  return (
    <section className="how-terraform">
      <img
        src="/assets/registry-flow.png"
        alt="Registry packages flow into Terraform and provision infrastructure"
      />
      <div>
        <h2>How Terraform, providers and modules work</h2>
        <p>
          <strong>Terraform</strong> plans and applies infrastructure changes
          from configuration written in HashiCorp Configuration Language.
        </p>
        <p>
          <strong>Providers</strong> connect Terraform to cloud and service
          APIs. <strong>Modules</strong> package reusable configurations for
          consistent infrastructure delivery.
        </p>
        <p>
          <strong>The Registry</strong> helps your teams discover and reuse
          both. Add a provider or module to your configuration and run{" "}
          <code>terraform init</code> to retrieve it from the configured private
          source.
        </p>
        <div className="how-terraform-links">
          <Link to="/providers">
            Browse providers <ArrowRightIcon size={14} />
          </Link>
          <Link to="/modules">
            Browse modules <ArrowRightIcon size={14} />
          </Link>
        </div>
      </div>
    </section>
  );
}

function CatalogSection({
  eyebrow,
  description,
  href,
  loading,
  items,
  variant,
}: {
  eyebrow: string;
  description: string;
  href: string;
  loading: boolean;
  items: Parameters<typeof PackageCard>[0]["item"][];
  variant: "providers" | "modules";
}) {
  return (
    <section className={`home-catalog-section ${variant}`}>
      <div className="source-section-heading">
        <div>
          <p>
            {variant === "providers" ? (
              <GlobeSimpleIcon size={14} aria-hidden="true" />
            ) : null}
            {eyebrow}
          </p>
          <span>{description}</span>
        </div>
        <Link to={href}>
          See all <ArrowRightIcon size={15} />
        </Link>
      </div>
      {loading ? (
        <div className={`source-card-grid ${variant}`}>
          {Array.from({ length: variant === "providers" ? 6 : 3 }).map(
            (_, index) => (
              <div className="source-card-skeleton skeleton" key={index} />
            ),
          )}
        </div>
      ) : items.length > 0 ? (
        <div className={`source-card-grid ${variant}`}>
          {items.map((item) => (
            <PackageCard
              key={`${item.kind}-${item.namespace}-${item.name}`}
              item={item}
            />
          ))}
        </div>
      ) : (
        <StatePanel kind="empty" />
      )}
    </section>
  );
}
