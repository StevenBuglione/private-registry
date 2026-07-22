import {
  ArrowRightIcon,
  BookOpenTextIcon,
  CubeIcon,
  InfoIcon,
  PackageIcon,
  ShieldCheckIcon,
} from "@phosphor-icons/react";
import { Link } from "react-router";
import { PackageCard } from "../components/PackageCard";
import { RegistryMark } from "../components/RegistryMark";
import { StatePanel } from "../components/StatePanel";
import { useCatalogPage } from "../hooks";
import { useRegistry } from "../registry-context";

export function HomePage() {
  const { selectedApmId, session } = useRegistry();
  const providers = useCatalogPage({
    kind: "provider",
    apmId: selectedApmId,
    approval: "approved",
    sort: "updated",
    limit: 6,
  });
  const modules = useCatalogPage({
    kind: "module",
    apmId: selectedApmId,
    approval: "approved",
    sort: "updated",
    limit: 5,
  });
  const activeApm = session.apms.find((item) => item.id === selectedApmId);
  const providerCount = providers.data?.total ?? 0;
  const moduleCount = modules.data?.total ?? 0;
  const catalogError = providers.isError || modules.isError;

  return (
    <div className="home-page">
      <section className="home-announcement">
        <div className="source-container">
          <InfoIcon size={20} weight="regular" />
          <div>
            <strong>Your private registry is ready</strong>
            <p>
              Packages are filtered to your current enterprise access context.
              Browse only the infrastructure approved for your teams.
            </p>
          </div>
        </div>
      </section>

      <section className="registry-hero source-container">
        <RegistryMark />
        <h1>Registry</h1>
        <p>
          Discover approved providers and reusable modules for building secure,
          reliable infrastructure.
        </p>
        <div className="hero-actions">
          <Link to="/providers">
            <PackageIcon size={19} /> Browse Providers
          </Link>
          <Link to="/modules">
            <CubeIcon size={19} /> Browse Modules
          </Link>
          <Link to="/docs">
            <BookOpenTextIcon size={19} /> Read Documentation
          </Link>
        </div>
        <div className="hero-counts">
          <strong>{providerCount}</strong> providers,{" "}
          <strong>{moduleCount}</strong> modules available to you
        </div>
      </section>

      <section className="access-strip">
        <div className="source-container">
          <ShieldCheckIcon size={20} />
          <strong>
            {activeApm
              ? `${activeApm.id} · ${activeApm.name}`
              : "Registry administrator"}
          </strong>
          <span>
            Your counts, search results, documentation, and live updates use
            this access context.
          </span>
          <Link to="/docs#access">
            Learn more <ArrowRightIcon size={14} />
          </Link>
        </div>
      </section>

      {catalogError ? (
        <div className="source-container">
          <StatePanel
            kind="api-error"
            action={() => window.location.reload()}
          />
        </div>
      ) : (
        <div className="home-catalog source-container">
          <CatalogSection
            eyebrow="Featured providers"
            description="Popular infrastructure plugins approved for your access context."
            href="/providers"
            loading={providers.isPending}
            items={providers.data?.items ?? []}
            variant="providers"
          />
          <CatalogSection
            eyebrow="Featured modules"
            description="Reusable, governed infrastructure configurations maintained by your platform teams."
            href="/modules"
            loading={modules.isPending}
            items={modules.data?.items ?? []}
            variant="modules"
          />
        </div>
      )}
    </div>
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
          <p>{eyebrow}</p>
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
      ) : items.length ? (
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
