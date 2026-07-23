import {
  ActivityIcon,
  ArrowClockwiseIcon,
  ChartBarIcon,
  GearIcon,
  HouseLineIcon,
  KeyIcon,
  ListMagnifyingGlassIcon,
  ShieldCheckIcon,
  WarningCircleIcon,
} from "@phosphor-icons/react";
import type { ReactNode } from "react";
import { useSearchParams } from "react-router";
import { HomepageSettingsPanel } from "../components/HomepageSettingsPanel";
import { StatePanel } from "../components/StatePanel";
import { SyncCredentialsPanel } from "../components/SyncCredentialsPanel";
import {
  useAdminDashboard,
  useAdminOperations,
  useAuditEvents,
} from "../hooks";
import type { AdminDashboard, AuditEvent, OperationalEvent } from "../types";
import { useRegistry } from "../use-registry";
import {
  formatBytes,
  formatDateTime,
  formatDuration,
  formatRelativeDate,
} from "../utils";

type AdminSection =
  | "overview"
  | "homepage"
  | "credentials"
  | "activity"
  | "audit";

const sections: {
  id: AdminSection;
  label: string;
  icon: ReactNode;
}[] = [
  { id: "overview", label: "Overview", icon: <ChartBarIcon size={18} /> },
  { id: "homepage", label: "Homepage", icon: <HouseLineIcon size={18} /> },
  { id: "credentials", label: "Sync credentials", icon: <KeyIcon size={18} /> },
  {
    id: "activity",
    label: "Operational logs",
    icon: <ActivityIcon size={18} />,
  },
  {
    id: "audit",
    label: "Audit log",
    icon: <ListMagnifyingGlassIcon size={18} />,
  },
];

export function AdminSettingsPage() {
  const { session } = useRegistry();
  const [searchParams, setSearchParams] = useSearchParams();
  if (!session.admin) {
    return (
      <div className="page-shell admin-access-denied">
        <StatePanel kind="no-access" />
      </div>
    );
  }
  const selected = sectionFrom(searchParams.get("section"));
  return (
    <div className="admin-page">
      <header className="admin-page-header">
        <div className="page-shell">
          <div className="admin-title-icon">
            <GearIcon size={23} weight="fill" />
          </div>
          <div>
            <span>Registry administration</span>
            <h1>Settings and operations</h1>
            <p>
              Configure the public experience, monitor ingestion, and manage
              automation access.
            </p>
          </div>
        </div>
      </header>
      <div className="page-shell admin-layout">
        <nav className="admin-navigation" aria-label="Administration sections">
          {sections.map((section) => (
            <button
              aria-label={section.label}
              className={selected === section.id ? "active" : ""}
              type="button"
              key={section.id}
              onClick={() => {
                setSearchParams(
                  section.id === "overview" ? {} : { section: section.id },
                );
              }}
            >
              {section.icon}
              <span>{section.label}</span>
            </button>
          ))}
        </nav>
        <section
          className="admin-content"
          aria-label="Administration content"
          aria-live="polite"
        >
          {selected === "overview" ? <OverviewPanel /> : null}
          {selected === "homepage" ? <HomepageSettingsPanel /> : null}
          {selected === "credentials" ? <SyncCredentialsPanel /> : null}
          {selected === "activity" ? <OperationsPanel /> : null}
          {selected === "audit" ? <AuditPanel /> : null}
        </section>
      </div>
    </div>
  );
}

function OverviewPanel() {
  const dashboard = useAdminDashboard();
  if (dashboard.isPending) return <StatePanel kind="loading" />;
  if (dashboard.isError) {
    return (
      <StatePanel
        kind="api-error"
        action={() => {
          void dashboard.refetch();
        }}
      />
    );
  }
  return (
    <Overview
      dashboard={dashboard.data}
      refresh={() => void dashboard.refetch()}
    />
  );
}

function Overview({
  dashboard,
  refresh,
}: {
  dashboard: AdminDashboard;
  refresh: () => void;
}) {
  return (
    <div className="admin-overview">
      <div className="admin-section-toolbar">
        <div>
          <h2>Operational overview</h2>
          <p>Live catalog, queue, ingestion, and dependency health.</p>
        </div>
        <button type="button" onClick={refresh}>
          <ArrowClockwiseIcon size={16} /> Refresh
        </button>
      </div>
      <div className={`admin-health-banner ${dashboard.status}`} role="status">
        {dashboard.status === "healthy" ? (
          <ShieldCheckIcon size={23} weight="fill" />
        ) : (
          <WarningCircleIcon size={23} weight="fill" />
        )}
        <div>
          <strong>
            {dashboard.status === "healthy"
              ? "Registry systems are healthy"
              : "Registry requires attention"}
          </strong>
          <p>
            Updated {formatRelativeDate(dashboard.generatedAt)} · PostgreSQL
            database {formatBytes(dashboard.databaseSizeBytes)}
          </p>
        </div>
      </div>
      <section className="admin-metric-section">
        <h3>Catalog</h3>
        <div className="admin-metric-grid">
          <Metric label="Providers" value={dashboard.catalog.providers} />
          <Metric label="Modules" value={dashboard.catalog.modules} />
          <Metric
            label="Active versions"
            value={dashboard.catalog.activeVersions}
          />
          <Metric label="Documents" value={dashboard.catalog.documents} />
          <Metric label="Downloads" value={dashboard.catalog.downloads} />
        </div>
      </section>
      <section className="admin-metric-section">
        <h3>Event processing</h3>
        <div className="admin-metric-grid">
          <Metric label="Queued" value={dashboard.queue.queued} />
          <Metric label="Processing" value={dashboard.queue.processing} />
          <Metric
            label="Retrying"
            value={dashboard.queue.retry}
            tone="warning"
          />
          <Metric
            label="Dead letter"
            value={dashboard.queue.deadLetter}
            tone={dashboard.queue.deadLetter > 0 ? "danger" : "default"}
          />
          <Metric
            label="P95 ingestion"
            value={formatDuration(dashboard.ingestion.latencyP95Ms)}
          />
        </div>
      </section>
      <div className="admin-overview-columns">
        <section className="admin-summary-card">
          <header>
            <h3>Dependencies</h3>
            <span>
              {dashboard.workerEnabled ? "Worker enabled" : "Worker disabled"}
            </span>
          </header>
          <dl className="admin-definition-list">
            {Object.entries(dashboard.dependencies).map(([name, status]) => (
              <div key={name}>
                <dt>{name}</dt>
                <dd>
                  <span
                    className={`dependency-dot ${status.replaceAll("_", "-")}`}
                  />
                  {status.replace("_", " ")}
                </dd>
              </div>
            ))}
          </dl>
        </section>
        <section className="admin-summary-card">
          <header>
            <h3>Last reconciliation</h3>
            <span>{dashboard.reconciliation?.status ?? "Not run"}</span>
          </header>
          {dashboard.reconciliation === undefined ? (
            <p>No reconciliation run has been recorded.</p>
          ) : (
            <dl className="admin-definition-list">
              <Definition
                label="Started"
                value={formatDateTime(dashboard.reconciliation.startedAt)}
              />
              <Definition
                label="Scope"
                value={dashboard.reconciliation.scope.replaceAll("-", " ")}
              />
              <Definition
                label="Discrepancies"
                value={dashboard.reconciliation.discrepancies.toLocaleString()}
              />
              <Definition
                label="Repaired"
                value={dashboard.reconciliation.repaired.toLocaleString()}
              />
            </dl>
          )}
        </section>
      </div>
    </div>
  );
}

function OperationsPanel() {
  const operations = useAdminOperations();
  if (operations.isPending) return <StatePanel kind="loading" />;
  if (operations.isError) {
    return (
      <StatePanel
        kind="api-error"
        action={() => {
          void operations.refetch();
        }}
      />
    );
  }
  return (
    <LogPanel
      title="Operational logs"
      description="Structured ingestion, retry, dead-letter, and reconciliation activity. Payloads and secrets are intentionally excluded."
      rows={operations.data}
      row={(event) => <OperationRow event={event} />}
    />
  );
}

function AuditPanel() {
  const audit = useAuditEvents();
  if (audit.isPending) return <StatePanel kind="loading" />;
  if (audit.isError) {
    return (
      <StatePanel
        kind="api-error"
        action={() => {
          void audit.refetch();
        }}
      />
    );
  }
  return (
    <LogPanel
      title="Administrator audit log"
      description="Immutable records of settings changes, credential lifecycle actions, and runner-triggered syncs."
      rows={audit.data}
      row={(event) => <AuditRow event={event} />}
    />
  );
}

function LogPanel<
  T extends { id?: string | undefined; eventId?: string | undefined },
>({
  title,
  description,
  rows,
  row,
}: {
  title: string;
  description: string;
  rows: T[];
  row: (value: T) => ReactNode;
}) {
  return (
    <div className="admin-log-panel">
      <div className="admin-section-toolbar">
        <div>
          <h2>{title}</h2>
          <p>{description}</p>
        </div>
        <span>{rows.length} recent events</span>
      </div>
      {rows.length === 0 ? (
        <div className="admin-empty">
          <ActivityIcon size={25} />
          <strong>No activity recorded</strong>
          <p>Events will appear here as the Registry is operated.</p>
        </div>
      ) : (
        <div className="admin-event-list">{rows.map(row)}</div>
      )}
    </div>
  );
}

function OperationRow({ event }: { event: OperationalEvent }) {
  return (
    <article className="admin-event-row" key={event.eventId}>
      <span className={`admin-event-icon ${event.status.replaceAll("_", "-")}`}>
        <ActivityIcon size={17} />
      </span>
      <div>
        <div className="admin-event-heading">
          <strong>{event.title.replaceAll("_", " ")}</strong>
          <span className={`admin-status ${statusClass(event.status)}`}>
            {event.status.replace("_", " ")}
          </span>
        </div>
        <p>{event.detail}</p>
        {event.repository === undefined ? null : (
          <code>
            {event.repository}/{event.path}
          </code>
        )}
      </div>
      <time dateTime={event.occurredAt}>
        {formatDateTime(event.occurredAt)}
      </time>
    </article>
  );
}

function AuditRow({ event }: { event: AuditEvent }) {
  return (
    <article className="admin-event-row audit" key={event.id}>
      <span className="admin-event-icon completed">
        <ShieldCheckIcon size={17} />
      </span>
      <div>
        <div className="admin-event-heading">
          <strong>
            {event.action.replaceAll(".", " · ").replaceAll("_", " ")}
          </strong>
          <span className="admin-status active">{event.actorType}</span>
        </div>
        <p>
          {event.actorId} updated {event.resourceType.replaceAll("_", " ")}{" "}
          <code>{event.resourceId}</code>
        </p>
        <details className="admin-audit-detail">
          <summary>View recorded changes</summary>
          <pre>{formatAuditDetail(event.detail)}</pre>
        </details>
        <small>Correlation {event.correlationId}</small>
      </div>
      <time dateTime={event.occurredAt}>
        {formatDateTime(event.occurredAt)}
      </time>
    </article>
  );
}

function Metric({
  label,
  value,
  tone = "default",
}: {
  label: string;
  value: number | string;
  tone?: "default" | "warning" | "danger";
}) {
  return (
    <div className={`admin-metric ${tone}`}>
      <strong>
        {typeof value === "number" ? value.toLocaleString() : value}
      </strong>
      <span>{label}</span>
    </div>
  );
}

function Definition({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function sectionFrom(value: string | null): AdminSection {
  if (
    value === "homepage" ||
    value === "credentials" ||
    value === "activity" ||
    value === "audit"
  ) {
    return value;
  }
  return "overview";
}

function statusClass(status: string): string {
  if (
    status === "failed" ||
    status === "dead_letter" ||
    status === "quarantined"
  )
    return "revoked";
  if (status === "retry" || status === "processing") return "expired";
  return "active";
}

function formatAuditDetail(detail: unknown): string {
  return detail === undefined ? "{}" : JSON.stringify(detail, null, 2);
}
