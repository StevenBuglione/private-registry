import {
  ArrowClockwiseIcon,
  ClockCounterClockwiseIcon,
  EyeIcon,
  ShieldCheckIcon,
  UsersThreeIcon,
} from "@phosphor-icons/react";
import { type ReactNode, useState } from "react";
import { useTrafficReport } from "../hooks";
import type { DailyTraffic } from "../types";
import { formatDateTime, formatRelativeDate } from "../utils";
import { StatePanel } from "./StatePanel";

const reportingPeriods = [7, 30, 90] as const;

export function TrafficAnalyticsPanel() {
  const [days, setDays] = useState(30);
  const traffic = useTrafficReport(days);

  if (traffic.isPending) return <StatePanel kind="loading" />;
  if (traffic.isError) {
    return (
      <StatePanel
        kind="api-error"
        action={() => {
          void traffic.refetch();
        }}
      />
    );
  }

  const report = traffic.data;
  return (
    <div className="traffic-analytics">
      <div className="admin-section-toolbar traffic-toolbar">
        <div>
          <h2>Traffic analytics</h2>
          <p>
            Authenticated page views, popular Registry routes, and signed-in
            visitor activity.
          </p>
        </div>
        <div className="traffic-toolbar-actions">
          <label>
            <span>Reporting period</span>
            <select
              aria-label="Traffic reporting period"
              value={days}
              onChange={(event) => {
                setDays(Number(event.target.value));
              }}
            >
              {reportingPeriods.map((period) => (
                <option value={period} key={period}>
                  Last {period} days
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            onClick={() => {
              void traffic.refetch();
            }}
          >
            <ArrowClockwiseIcon size={16} /> Refresh
          </button>
        </div>
      </div>

      <div className="traffic-summary-grid">
        <TrafficMetric
          label={`Page views · ${report.days.toString()} days`}
          value={report.summary.pageViews}
          icon={<EyeIcon size={19} />}
        />
        <TrafficMetric
          label="Unique visitors"
          value={report.summary.uniqueVisitors}
          icon={<UsersThreeIcon size={19} />}
        />
        <TrafficMetric
          label="Views today"
          value={report.summary.pageViewsToday}
          icon={<ClockCounterClockwiseIcon size={19} />}
        />
        <TrafficMetric
          label="Visitors today"
          value={report.summary.visitorsToday}
          icon={<ShieldCheckIcon size={19} />}
        />
      </div>

      <section className="traffic-section traffic-trend-section">
        <header>
          <div>
            <h3>Traffic trend</h3>
            <p>Daily page views and unique authenticated visitors in UTC.</p>
          </div>
          <span>Updated {formatRelativeDate(report.generatedAt)}</span>
        </header>
        <TrafficChart daily={report.daily} />
      </section>

      <div className="traffic-columns">
        <section className="traffic-section">
          <header>
            <div>
              <h3>Top pages</h3>
              <p>Most-viewed Registry routes during this period.</p>
            </div>
          </header>
          {report.topRoutes.length === 0 ? (
            <TrafficEmpty message="No page views have been recorded yet." />
          ) : (
            <div className="admin-table-wrap traffic-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th scope="col">Page</th>
                    <th scope="col">Views</th>
                    <th scope="col">Visitors</th>
                    <th scope="col">Last viewed</th>
                  </tr>
                </thead>
                <tbody>
                  {report.topRoutes.map((route) => (
                    <tr key={route.path}>
                      <td>
                        <code>{route.path}</code>
                      </td>
                      <td>{route.pageViews.toLocaleString()}</td>
                      <td>{route.uniqueVisitors.toLocaleString()}</td>
                      <td>{formatRelativeDate(route.lastViewedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="traffic-section">
          <header>
            <div>
              <h3>Recent access</h3>
              <p>Latest authenticated navigation across the Registry.</p>
            </div>
          </header>
          {report.recentAccess.length === 0 ? (
            <TrafficEmpty message="No recent access has been recorded." />
          ) : (
            <ol className="recent-access-list">
              {report.recentAccess.slice(0, 12).map((access, index) => (
                <li
                  key={`${access.subject}-${access.occurredAt}-${index.toString()}`}
                >
                  <span className="traffic-avatar" aria-hidden="true">
                    {initials(access.displayName)}
                  </span>
                  <div>
                    <strong>{access.displayName}</strong>
                    <span>{access.email ?? access.subject}</span>
                    <code>{access.path}</code>
                  </div>
                  <time
                    dateTime={access.occurredAt}
                    title={formatDateTime(access.occurredAt)}
                  >
                    {formatRelativeDate(access.occurredAt)}
                  </time>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>

      <section className="traffic-section">
        <header>
          <div>
            <h3>Visitors</h3>
            <p>
              Signed-in people who accessed the Registry during this period.
            </p>
          </div>
          <span>{report.visitors.length} shown</span>
        </header>
        {report.visitors.length === 0 ? (
          <TrafficEmpty message="No visitors have been recorded yet." />
        ) : (
          <div className="admin-table-wrap traffic-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th scope="col">Visitor</th>
                  <th scope="col">Page views</th>
                  <th scope="col">First seen</th>
                  <th scope="col">Last seen</th>
                  <th scope="col">Last page</th>
                </tr>
              </thead>
              <tbody>
                {report.visitors.map((visitor) => (
                  <tr key={visitor.subject}>
                    <td>
                      <div className="traffic-visitor">
                        <span className="traffic-avatar" aria-hidden="true">
                          {initials(visitor.displayName)}
                        </span>
                        <span>
                          <strong>{visitor.displayName}</strong>
                          <small>{visitor.email ?? visitor.subject}</small>
                        </span>
                      </div>
                    </td>
                    <td>{visitor.pageViews.toLocaleString()}</td>
                    <td title={formatDateTime(visitor.firstSeenAt)}>
                      {formatRelativeDate(visitor.firstSeenAt)}
                    </td>
                    <td title={formatDateTime(visitor.lastSeenAt)}>
                      {formatRelativeDate(visitor.lastSeenAt)}
                    </td>
                    <td>
                      <code>{visitor.lastPath}</code>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <div className="admin-security-note traffic-privacy-note">
        <ShieldCheckIcon size={18} />
        <p>
          Analytics stores the signed-in subject, display name, email, page
          path, and timestamp for 180 days. Query strings, URL fragments,
          request bodies, tokens, IP addresses, and browser fingerprints are not
          collected.
        </p>
      </div>
    </div>
  );
}

function TrafficMetric({
  label,
  value,
  icon,
}: {
  label: string;
  value: number;
  icon: ReactNode;
}) {
  return (
    <article>
      <span>{icon}</span>
      <div>
        <strong>{value.toLocaleString()}</strong>
        <small>{label}</small>
      </div>
    </article>
  );
}

function TrafficChart({ daily }: { daily: DailyTraffic[] }) {
  const maximum = Math.max(...daily.map((day) => day.pageViews), 1);
  return (
    <div className="traffic-chart">
      <div className="traffic-chart-plot">
        {daily.map((day) => {
          const height =
            day.pageViews === 0
              ? 0
              : Math.max((day.pageViews / maximum) * 100, 3);
          return (
            <div
              className="traffic-chart-day"
              key={day.day}
              title={`${shortDate(day.day)}: ${day.pageViews.toLocaleString()} views, ${day.uniqueVisitors.toLocaleString()} visitors`}
            >
              <span
                style={{ height: `${height.toString()}%` }}
                aria-hidden="true"
              />
            </div>
          );
        })}
      </div>
      <div className="traffic-chart-axis" aria-hidden="true">
        <span>{daily[0] === undefined ? "" : shortDate(daily[0].day)}</span>
        <span>
          {daily.length === 0 ? "" : shortDate(daily.at(-1)?.day ?? "")}
        </span>
      </div>
      <p className="sr-only">
        {daily
          .map(
            (day) =>
              `${shortDate(day.day)}: ${day.pageViews.toString()} page views and ${day.uniqueVisitors.toString()} unique visitors`,
          )
          .join(". ")}
      </p>
    </div>
  );
}

function TrafficEmpty({ message }: { message: string }) {
  return <p className="traffic-empty">{message}</p>;
}

function initials(displayName: string): string {
  return displayName
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0] ?? "")
    .join("")
    .toUpperCase();
}

function shortDate(value: string): string {
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat("en-US", {
        month: "short",
        day: "numeric",
        timeZone: "UTC",
      }).format(date);
}
