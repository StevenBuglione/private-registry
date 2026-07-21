import { useProviderParams } from "@/routes/Provider/hooks/useProviderParams";
import { EnterprisePackagePanel } from "./EnterprisePackagePanel";

const hclName = (value: string): string =>
  value.replace(/[^A-Za-z0-9_]/g, "_").replace(/^[0-9]/, "_$&");

export function ProviderEnterprisePanel() {
  const { namespace, provider, version } = useProviderParams();
  if (!namespace || !provider || !version) return null;
  return (
    <EnterprisePackagePanel
      packageId={`provider/${namespace}/${provider}`}
      kind="provider"
      localName={hclName(provider)}
      version={version}
    />
  );
}
