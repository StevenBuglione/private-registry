import type { EnterprisePackageMetadata } from "./types";
import { GovernanceBadges } from "./GovernanceBadges";

type Props = { metadata: EnterprisePackageMetadata };

export function EnterpriseMetadataPanel({ metadata }: Props) {
  return (
    <aside aria-labelledby="enterprise-metadata-heading">
      <h2 id="enterprise-metadata-heading">Governance</h2>
      <GovernanceBadges
        supportLevel={metadata.supportLevel}
        lifecycle={metadata.lifecycle}
        riskTier={metadata.riskTier}
        verification={metadata.verification}
      />
      <dl>
        <dt>Owners</dt>
        <dd>{metadata.owners.join(", ")}</dd>
        <dt>Approvals</dt>
        <dd>
          {
            metadata.approvals.filter((item) => item.status === "approved")
              .length
          }{" "}
          approved
        </dd>
      </dl>
    </aside>
  );
}
