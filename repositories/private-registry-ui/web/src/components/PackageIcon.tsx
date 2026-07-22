import {
  BracketsCurlyIcon,
  CubeIcon,
  PackageIcon as ProviderIcon,
} from "@phosphor-icons/react";
import type { PackageKind } from "../types";

export function PackageIcon({
  kind,
  name,
  size = "medium",
}: {
  kind: PackageKind;
  name?: string;
  size?: "small" | "medium" | "large";
}) {
  const logo = providerLogo(name);
  const Icon =
    kind === "provider"
      ? ProviderIcon
      : name?.toLowerCase().includes("module") === true
        ? BracketsCurlyIcon
        : CubeIcon;
  return (
    <span
      className={`package-icon package-icon-${kind} package-icon-${size}`}
      aria-hidden="true"
    >
      {logo !== undefined ? (
        <img src={logo} alt="" />
      ) : (
        <Icon weight="regular" />
      )}
    </span>
  );
}

function providerLogo(name?: string): string | undefined {
  const normalized = name?.toLowerCase().replaceAll(/[^a-z0-9]/g, "") ?? "";
  if (
    normalized.includes("azurerm") ||
    normalized === "azure" ||
    normalized.includes("azuread")
  )
    return "/assets/providers/azurerm.png";
  if (normalized === "aws") return "/assets/providers/aws.png";
  if (normalized.includes("google")) return "/assets/providers/google.svg";
  if (normalized.includes("kubernetes"))
    return "/assets/providers/kubernetes.svg";
  if (normalized.includes("helm")) return "/assets/providers/helm.svg";
  if (normalized.includes("datadog")) return "/assets/providers/datadog.png";
  if (normalized.includes("grafana")) return "/assets/providers/grafana.png";
  return undefined;
}
