import { XIcon } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import type { Approval, Lifecycle, PackageKind, Risk } from "../types";

export interface FilterState {
  kind?: PackageKind;
  provider?: string;
  lifecycle?: Lifecycle;
  approval?: Approval;
  risk?: Risk;
}

export function Filters({
  value,
  kindLocked,
  mobileOpen,
  onMobileToggle,
  onChange,
  onClear,
}: {
  value: FilterState;
  kindLocked?: PackageKind;
  mobileOpen: boolean;
  onMobileToggle: () => void;
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
          Clear all
        </button>
        <button
          className="filter-close"
          type="button"
          onClick={onMobileToggle}
          aria-label="Close filters"
        >
          <XIcon size={18} />
        </button>
      </div>
      {!kindLocked ? (
        <FilterGroup label="Type">
          <RadioFilter
            label="All packages"
            checked={!value.kind}
            onChange={() => onChange("kind", undefined)}
          />
          <RadioFilter
            label="Providers"
            checked={value.kind === "provider"}
            onChange={() => onChange("kind", "provider")}
          />
          <RadioFilter
            label="Modules"
            checked={value.kind === "module"}
            onChange={() => onChange("kind", "module")}
          />
        </FilterGroup>
      ) : null}
      <FilterGroup label="Approval">
        {(["approved", "rejected", "waived"] as Approval[]).map((item) => (
          <RadioFilter
            key={item}
            label={capitalize(item)}
            checked={value.approval === item}
            onChange={() =>
              onChange("approval", value.approval === item ? undefined : item)
            }
          />
        ))}
      </FilterGroup>
      <FilterGroup label="Lifecycle">
        {(
          [
            "draft",
            "candidate",
            "approved",
            "maintenance",
            "deprecated",
            "revoked",
            "archived",
          ] as Lifecycle[]
        ).map((item) => (
          <RadioFilter
            key={item}
            label={capitalize(item)}
            checked={value.lifecycle === item}
            onChange={() =>
              onChange("lifecycle", value.lifecycle === item ? undefined : item)
            }
          />
        ))}
      </FilterGroup>
      <FilterGroup label="Risk">
        {(["low", "medium", "high", "critical"] as Risk[]).map((item) => (
          <RadioFilter
            key={item}
            label={capitalize(item)}
            checked={value.risk === item}
            onChange={() =>
              onChange("risk", value.risk === item ? undefined : item)
            }
          />
        ))}
      </FilterGroup>
    </aside>
  );
}

function FilterGroup({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <fieldset className="filter-group">
      <legend>{label}</legend>
      {children}
    </fieldset>
  );
}

function RadioFilter({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: () => void;
}) {
  return (
    <label className="filter-option">
      <input type="checkbox" checked={checked} onChange={onChange} />
      <span>{label}</span>
    </label>
  );
}

function capitalize(value: string) {
  return value.charAt(0).toUpperCase() + value.slice(1);
}
