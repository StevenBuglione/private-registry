import {
  ArrowSquareOutIcon,
  ClockIcon,
  SealCheckIcon,
} from "@phosphor-icons/react";
import { Link } from "react-router";
import type { PackageSummary } from "../types";
import { formatRelativeDate, packageHref } from "../utils";
import { PackageIcon } from "./PackageIcon";

export function PackageCard({ item }: { item: PackageSummary }) {
  if (item.kind === "module") {
    return (
      <Link className="package-card module-card" to={packageHref(item)}>
        <PackageIcon kind={item.kind} name={item.provider} />
        <div className="module-card-content">
          <strong>
            <span>{item.namespace}</span>
            <b>/</b>
            {item.name}
          </strong>
          <p>{item.description}</p>
          <div>
            <span>
              <ClockIcon size={16} /> {formatRelativeDate(item.updatedAt)}
            </span>
            <span>{item.provider} provider</span>
            <em>v{item.version}</em>
          </div>
        </div>
      </Link>
    );
  }

  return (
    <Link className="package-card provider-card" to={packageHref(item)}>
      <PackageIcon kind={item.kind} name={item.name} />
      <div className="provider-card-content">
        <strong>{providerDisplayName(item.name)}</strong>
        <span>by {item.owner}</span>
        <small>
          Docs <ArrowSquareOutIcon size={13} />
        </small>
      </div>
      {item.verified ? (
        <span className="source-tier">
          <SealCheckIcon size={16} />
          <span className="sr-only">Verified</span>
        </span>
      ) : null}
    </Link>
  );
}

function providerDisplayName(name: string): string {
  const labels: Record<string, string> = {
    aws: "AWS",
    azurerm: "Azure",
    azuread: "Azure Active Directory",
    google: "Google Cloud Platform",
    kubernetes: "Kubernetes",
    helm: "Helm",
    random: "Random",
    null: "Null",
    tls: "TLS",
    time: "Time",
    datadog: "Datadog",
    grafana: "Grafana",
  };
  return labels[name.toLowerCase()] ?? name;
}
