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
  if (!value) return "Recently";
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
