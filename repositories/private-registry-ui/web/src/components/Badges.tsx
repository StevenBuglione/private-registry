import { CheckCircleIcon } from "@phosphor-icons/react";

export function VerificationBadge({
  label,
  tone = "official",
}: {
  label: string;
  tone?: "official" | "partner";
}) {
  return (
    <span className={`badge badge-${tone}`}>
      <CheckCircleIcon size={14} weight="bold" />
      {label}
    </span>
  );
}
