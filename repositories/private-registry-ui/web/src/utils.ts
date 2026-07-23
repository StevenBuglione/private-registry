import type { PackageSummary } from "./types";

export function packageHref(
  item: Pick<
    PackageSummary,
    "kind" | "namespace" | "name" | "target" | "version"
  >,
): string {
  if (item.kind === "provider") {
    return `/providers/${encodeURIComponent(item.namespace)}/${encodeURIComponent(item.name)}/${encodeURIComponent(item.version)}`;
  }
  return `/modules/${encodeURIComponent(item.namespace)}/${encodeURIComponent(item.name)}/${encodeURIComponent(item.target ?? "general")}/${encodeURIComponent(item.version)}`;
}

export function formatRelativeDate(value: string): string {
  if (value.length === 0) return "Recently";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const seconds = Math.round((date.getTime() - Date.now()) / 1000);
  const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: "auto" });
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ["year", 31_536_000],
    ["month", 2_592_000],
    ["week", 604_800],
    ["day", 86_400],
    ["hour", 3_600],
    ["minute", 60],
  ];
  for (const [unit, divisor] of units) {
    if (Math.abs(seconds) >= divisor)
      return formatter.format(Math.round(seconds / divisor), unit);
  }
  return "Just now";
}

export function hasText(value: string | null | undefined): value is string {
  return typeof value === "string" && value.length > 0;
}

export function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

export function formatBytes(value: number): string {
  if (value < 1024) return `${value.toLocaleString()} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let amount = value;
  let unit = 0;
  do {
    amount /= 1024;
    unit += 1;
  } while (amount >= 1024 && unit < units.length);
  return `${amount.toFixed(amount >= 10 ? 0 : 1)} ${units[unit - 1] ?? "B"}`;
}

export function formatDuration(milliseconds: number): string {
  if (milliseconds < 1000) return `${milliseconds.toLocaleString()} ms`;
  return `${(milliseconds / 1000).toFixed(1)} s`;
}
