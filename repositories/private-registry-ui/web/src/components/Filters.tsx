import { InfoIcon, XIcon } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import type { Approval, Lifecycle, PackageKind, Risk } from "../types";
import {
  moduleProviderOptions,
  providerCategoryOptions,
  providerTierOptions,
} from "./filter-options";
import { PackageIcon } from "./PackageIcon";

const providerTierValues = providerTierOptions.map((option) => option.value);

export interface FilterState {
  kind?: PackageKind | undefined;
  provider?: string | undefined;
  tier?: string | undefined;
  category?: string | undefined;
  lifecycle?: Lifecycle | undefined;
  approval?: Approval | undefined;
  risk?: Risk | undefined;
}

export function Filters({
  value,
  kindLocked,
  mobileOpen,
  onChange,
  onClear,
}: {
  value: FilterState;
  kindLocked?: PackageKind | undefined;
  mobileOpen: boolean;
  onChange: <K extends keyof FilterState>(
    key: K,
    value?: FilterState[K],
  ) => void;
  onClear: () => void;
}) {
  return (
    <aside
      className={`filters-panel ${mobileOpen ? "is-open" : ""}`}
      aria-label="Catalog filters"
    >
      <div className="filters-heading">
        <strong>Filters</strong>
        <button type="button" onClick={onClear}>
          <XIcon size={14} /> Clear Filters
        </button>
      </div>
      {kindLocked === "provider" ? (
        <ProviderFilters value={value} onChange={onChange} />
      ) : null}
      {kindLocked === "module" ? (
        <ModuleFilters value={value} onChange={onChange} />
      ) : null}
      {kindLocked === undefined ? (
        <FilterGroup label="Type">
          <FilterOption
            label="Providers"
            checked={value.kind === "provider"}
            onChange={() => {
              onChange(
                "kind",
                value.kind === "provider" ? undefined : "provider",
              );
            }}
          />
          <FilterOption
            label="Modules"
            checked={value.kind === "module"}
            onChange={() => {
              onChange("kind", value.kind === "module" ? undefined : "module");
            }}
          />
        </FilterGroup>
      ) : null}
    </aside>
  );
}

function ProviderFilters({
  value,
  onChange,
}: {
  value: FilterState;
  onChange: <K extends keyof FilterState>(
    key: K,
    value?: FilterState[K],
  ) => void;
}) {
  const tiers =
    value.tier === undefined ? providerTierValues : csvValues(value.tier);
  const categories = csvValues(value.category);
  return (
    <>
      <FilterGroup label="Tier" describedBy="provider-tier-note" hasInfo>
        <span id="provider-tier-note" className="sr-only">
          Select one or more provider publishing tiers.
        </span>
        {providerTierOptions.map((option) => (
          <FilterOption
            key={option.value}
            label={option.label}
            description={option.description}
            checked={tiers.includes(option.value)}
            onChange={() => {
              onChange(
                "tier",
                toggleCsvValue(tiers, option.value, providerTierValues, true),
              );
            }}
          />
        ))}
      </FilterGroup>
      <FilterGroup label="Category">
        {providerCategoryOptions.map(([option, label]) => (
          <FilterOption
            key={option}
            label={label}
            checked={categories.includes(option)}
            onChange={() => {
              onChange(
                "category",
                toggleCsvValue(
                  categories,
                  option,
                  providerCategoryOptions.map(([candidate]) => candidate),
                  false,
                ),
              );
            }}
          />
        ))}
      </FilterGroup>
    </>
  );
}

function ModuleFilters({
  value,
  onChange,
}: {
  value: FilterState;
  onChange: <K extends keyof FilterState>(
    key: K,
    value?: FilterState[K],
  ) => void;
}) {
  const providers = csvValues(value.provider);
  return (
    <>
      <FilterGroup label="Tier">
        <FilterOption
          label="Partner"
          description="Display only modules that are maintained by a Hashicorp partner."
          checked={value.tier === "partner"}
          onChange={() => {
            onChange("tier", value.tier === "partner" ? undefined : "partner");
          }}
        />
      </FilterGroup>
      <FilterGroup label="Provider">
        {moduleProviderOptions.map(([option, label]) => (
          <FilterOption
            key={option}
            label={label}
            icon={<PackageIcon kind="provider" name={option} size="small" />}
            checked={providers.includes(option)}
            onChange={() => {
              onChange(
                "provider",
                toggleCsvValue(
                  providers,
                  option,
                  moduleProviderOptions.map(([candidate]) => candidate),
                  false,
                ),
              );
            }}
          />
        ))}
      </FilterGroup>
    </>
  );
}

function FilterGroup({
  label,
  describedBy,
  hasInfo = false,
  children,
}: {
  label: string;
  describedBy?: string | undefined;
  hasInfo?: boolean | undefined;
  children: ReactNode;
}) {
  return (
    <fieldset className="filter-group" aria-describedby={describedBy}>
      <legend>
        <span className="filter-legend-label">
          {label}
          {hasInfo ? <InfoIcon size={14} aria-hidden="true" /> : null}
        </span>
      </legend>
      {children}
    </fieldset>
  );
}

function FilterOption({
  label,
  description,
  icon,
  checked,
  onChange,
}: {
  label: string;
  description?: string | undefined;
  icon?: ReactNode;
  checked: boolean;
  onChange: () => void;
}) {
  const className = [
    "filter-option",
    description === undefined ? "" : "has-help",
    icon === undefined ? "" : "has-icon",
  ]
    .filter((value) => value.length > 0)
    .join(" ");
  return (
    <label className={className}>
      <input type="checkbox" checked={checked} onChange={onChange} />
      {icon}
      <span>{label}</span>
      {description === undefined ? null : <small>{description}</small>}
    </label>
  );
}

function csvValues(value?: string): string[] {
  if (value === undefined || value === "none") return [];
  return value.split(",").filter((candidate) => candidate.length > 0);
}

function toggleCsvValue(
  selected: readonly string[],
  value: string,
  order: readonly string[],
  allMeansUndefined: boolean,
): string | undefined {
  const next = new Set(selected);
  if (next.has(value)) next.delete(value);
  else next.add(value);
  const ordered = order.filter((candidate) => next.has(candidate));
  if (allMeansUndefined && ordered.length === order.length) return undefined;
  if (ordered.length === 0) return allMeansUndefined ? "none" : undefined;
  return ordered.join(",");
}
