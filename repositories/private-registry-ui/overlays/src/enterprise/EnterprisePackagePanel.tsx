import { useQuery } from "@tanstack/react-query";
import { getEnterprisePackageMetadata } from "./api";
import { EnterpriseMetadataPanel } from "./EnterpriseMetadataPanel";
import { RegistrySourceSnippet } from "./RegistrySourceSnippet";

type Props = {
  packageId: string;
  kind: "module" | "provider";
  localName: string;
  version: string;
};

export function EnterprisePackagePanel({
  packageId,
  kind,
  localName,
  version,
}: Props) {
  const query = useQuery({
    queryKey: ["enterprise-package", packageId],
    queryFn: () => getEnterprisePackageMetadata(packageId),
  });

  if (query.isPending) {
    return (
      <div
        aria-label="Loading package governance"
        className="h-24 animate-pulse rounded bg-gray-500/10"
      />
    );
  }
  if (query.isError) {
    return (
      <p
        role="status"
        className="rounded border border-amber-500/40 p-3 text-sm"
      >
        Governance metadata is temporarily unavailable. Package documentation
        remains readable.
      </p>
    );
  }

  const metadata = query.data;
  return (
    <div className="grid gap-5 border-t border-gray-200 pt-5 dark:border-gray-700">
      <EnterpriseMetadataPanel metadata={metadata} />
      {metadata.sourceAddress ? (
        <RegistrySourceSnippet
          kind={kind}
          localName={localName}
          source={metadata.sourceAddress}
          version={metadata.versionConstraint || `= ${version}`}
        />
      ) : null}
    </div>
  );
}
