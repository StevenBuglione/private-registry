import { useModuleParams } from "@/routes/Module/hooks/useModuleParams";
import { EnterprisePackagePanel } from "./EnterprisePackagePanel";

const hclName = (value: string): string =>
  value.replace(/[^A-Za-z0-9_]/g, "_").replace(/^[0-9]/, "_$&");

export function ModuleEnterprisePanel() {
  const { namespace, name, target, version } = useModuleParams();
  if (!namespace || !name || !target || !version) return null;
  return (
    <EnterprisePackagePanel
      packageId={`module/${namespace}/${name}/${target}`}
      kind="module"
      localName={hclName(name)}
      version={version}
    />
  );
}
