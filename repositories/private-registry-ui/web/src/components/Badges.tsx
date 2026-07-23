import { CheckCircleIcon } from "@phosphor-icons/react";

export function VerificationBadge({ label }: { label: string }) {
  return (
    <span className="badge badge-verified">
      <CheckCircleIcon size={14} weight="bold" />
      {label}
    </span>
  );
}
