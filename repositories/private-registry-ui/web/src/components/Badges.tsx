import { CheckCircleIcon, WarningCircleIcon } from "@phosphor-icons/react";
import type { Approval, Lifecycle } from "../types";

export function ApprovalBadge({
  value,
  verified,
}: {
  value: Approval;
  verified?: boolean;
}) {
  const label =
    verified === true && value === "approved" ? "Verified" : capitalize(value);
  return (
    <span className={`badge badge-${value}`}>
      {value === "approved" ? (
        <CheckCircleIcon size={14} weight="bold" />
      ) : (
        <WarningCircleIcon size={14} weight="bold" />
      )}
      {label}
    </span>
  );
}

export function LifecycleBadge({ value }: { value: Lifecycle }) {
  return (
    <span className={`badge badge-lifecycle badge-${value}`}>
      <span className="badge-dot" aria-hidden="true" />
      {capitalize(value)}
    </span>
  );
}

function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}
