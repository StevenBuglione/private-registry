import type { EnterprisePackageMetadata } from "./types";

type Props = Pick<
  EnterprisePackageMetadata,
  "supportLevel" | "lifecycle" | "riskTier" | "verification"
>;

const label = (value: string): string =>
  value
    .split("-")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");

export function GovernanceBadges(props: Props) {
  return (
    <ul className="flex flex-wrap gap-2" aria-label="Package governance status">
      {Object.entries(props).map(([name, value]) => (
        <li
          key={name}
          className="rounded-full border border-current px-3 py-1 text-xs font-semibold"
          data-governance-kind={name}
          data-governance-value={value}
        >
          {label(value)}
        </li>
      ))}
    </ul>
  );
}
