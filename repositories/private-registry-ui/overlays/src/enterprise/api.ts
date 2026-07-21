import { getLoadedRegistryRuntimeConfig } from "./runtime-config";
import type { EnterprisePackageMetadata } from "./types";

type GovernanceResponse = {
  package_id: string;
  owners: string[];
  support_level: EnterprisePackageMetadata["supportLevel"];
  lifecycle: EnterprisePackageMetadata["lifecycle"];
  risk_tier: EnterprisePackageMetadata["riskTier"];
  verification: EnterprisePackageMetadata["verification"];
  approvals: EnterprisePackageMetadata["approvals"];
  source_address?: string;
  version_constraint?: string;
  support_url?: string;
  source_repository_url?: string;
  jfrog_console_url?: string;
};

export async function getEnterprisePackageMetadata(
  packageId: string,
): Promise<EnterprisePackageMetadata> {
  const config = getLoadedRegistryRuntimeConfig();
  const base = config.enterpriseApiUrl.replace(/\/$/, "");
  const encodedId = packageId.split("/").map(encodeURIComponent).join("/");
  const response = await fetch(`${base}/packages/${encodedId}/governance`, {
    credentials: "same-origin",
    headers: { Accept: "application/json" },
  });

  if (!response.ok) {
    throw new Error(`Unable to load package governance (${response.status})`);
  }

  const data = (await response.json()) as GovernanceResponse;
  return {
    packageId: data.package_id,
    owners: data.owners,
    supportLevel: data.support_level,
    lifecycle: data.lifecycle,
    riskTier: data.risk_tier,
    verification: data.verification,
    approvals: data.approvals,
    sourceAddress: data.source_address,
    versionConstraint: data.version_constraint,
    supportUrl: data.support_url,
    sourceRepositoryUrl: data.source_repository_url,
    jfrogConsoleUrl: data.jfrog_console_url,
  };
}
