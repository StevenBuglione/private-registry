import type { PackageDetail, PackageSymbol } from "../../types";
import { hasText } from "../../utils";

export type ModuleChildKind = "submodule" | "example";

export function formatDownloadCount(value: number | undefined): string {
  return value === undefined
    ? "—"
    : new Intl.NumberFormat("en-US").format(value);
}

export function shortExternalUrl(value: string): string {
  try {
    const url = new URL(value);
    return `${url.hostname}${url.pathname}`.replace(/\/$/, "");
  } catch {
    return value;
  }
}

export function sourceChildUrl(
  sourceRepository: string | undefined,
  sourceTag: string | undefined,
  path: string,
): string | undefined {
  if (!hasText(sourceRepository) || !hasText(sourceTag)) return undefined;
  try {
    const url = new URL(sourceRepository);
    if (url.hostname !== "github.com") return undefined;
    url.pathname = `${url.pathname.replace(/\/$/, "")}/tree/${encodeURIComponent(sourceTag)}/${path
      .split("/")
      .map(encodeURIComponent)
      .join("/")}`;
    return url.toString();
  } catch {
    return undefined;
  }
}

export function moduleRootHref(item: PackageDetail, version: string): string {
  return `/modules/${encodeURIComponent(item.namespace)}/${encodeURIComponent(item.name)}/${encodeURIComponent(item.target ?? item.provider)}/${encodeURIComponent(version)}`;
}

export function providerLatestHref(item: PackageDetail): string {
  return `/providers/${encodeURIComponent(item.namespace)}/${encodeURIComponent(item.name)}`;
}

export function namespaceHref(namespace: string): string {
  return `/namespaces/${encodeURIComponent(namespace)}`;
}

export function moduleChildHref(
  item: PackageDetail,
  version: string,
  variant: "submodules" | "examples",
  childName: string,
): string {
  return `${moduleRootHref(item, version)}/${variant}/${encodeURIComponent(childName)}`;
}

export function moduleTabCount(symbols: PackageSymbol[], tab: string): number {
  return symbolsForModuleTab(symbols, tab).length;
}

export function symbolsForModuleView(
  symbols: PackageSymbol[],
  childKind: ModuleChildKind | undefined,
  childName: string | undefined,
): PackageSymbol[] {
  if (childKind !== undefined && hasText(childName)) {
    const prefix = `${childKind === "submodule" ? "modules" : "examples"}/${childName}/`;
    return symbols.filter((symbol) => symbol.path.startsWith(prefix));
  }
  const rootSymbols = symbols.filter(
    (symbol) =>
      !symbol.path.startsWith("modules/") &&
      !symbol.path.startsWith("examples/") &&
      !["submodule", "example"].includes(normalizeSymbolKind(symbol.kind)),
  );
  const moduleInventory = symbols.filter(
    (symbol) =>
      !symbol.path.startsWith("examples/") &&
      ["dependency", "resource"].includes(normalizeSymbolKind(symbol.kind)),
  );
  return dedupeModuleSymbols([
    ...rootSymbols,
    ...moduleInventory.filter((symbol) =>
      ["dependency", "resource"].includes(normalizeSymbolKind(symbol.kind)),
    ),
  ]);
}

export function hasRootModuleConfiguration(symbols: PackageSymbol[]): boolean {
  return symbols.some(
    (symbol) =>
      !symbol.path.includes("/") &&
      !["submodule", "example"].includes(normalizeSymbolKind(symbol.kind)),
  );
}

export function symbolsForModuleTab(
  symbols: PackageSymbol[],
  tab: string,
): PackageSymbol[] {
  const accepted: Record<string, string[]> = {
    inputs: ["input", "variable"],
    outputs: ["output"],
    dependencies: [
      "dependency",
      "module_dependency",
      "provider_dependency",
      "provider_requirement",
    ],
    resources: ["resource"],
  };
  const kinds = accepted[tab] ?? [];
  return symbols.filter((symbol) =>
    kinds.includes(normalizeSymbolKind(symbol.kind)),
  );
}

export function providerDocumentGroups(
  symbols: PackageSymbol[],
  filter: string,
) {
  const normalizedFilter = filter.trim().toLowerCase();
  const matches = (symbol: PackageSymbol) =>
    normalizedFilter.length === 0 ||
    `${symbol.name} ${symbol.description ?? ""}`
      .toLowerCase()
      .includes(normalizedFilter);
  const filtered = symbols.filter(matches);
  const guides = filtered.filter((symbol) =>
    ["guide", "document", "overview"].includes(
      normalizeSymbolKind(symbol.kind),
    ),
  );
  const functions = filtered.filter(
    (symbol) => normalizeSymbolKind(symbol.kind) === "function",
  );
  const categorySymbols = filtered.filter((symbol) =>
    ["resource", "data_source", "datasource", "list_resource"].includes(
      normalizeSymbolKind(symbol.kind),
    ),
  );
  const categories = new Map<string, PackageSymbol[]>();
  for (const symbol of categorySymbols) {
    const category = providerServiceCategory(symbol.name);
    categories.set(category, [...(categories.get(category) ?? []), symbol]);
  }

  const standalone = [
    { label: "Guides", items: guides },
    { label: "Functions", items: functions },
  ]
    .filter((group) => group.items.length > 0)
    .map((group) => ({
      label: group.label,
      sections: [{ label: group.label, items: group.items }],
    }));
  const services = [...categories.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([label, items]) => ({
      label,
      sections: [
        {
          label: "Resources",
          items: items.filter(
            (symbol) => normalizeSymbolKind(symbol.kind) === "resource",
          ),
        },
        {
          label: "Data Sources",
          items: items.filter((symbol) =>
            ["data_source", "datasource"].includes(
              normalizeSymbolKind(symbol.kind),
            ),
          ),
        },
        {
          label: "List Resources",
          items: items.filter(
            (symbol) => normalizeSymbolKind(symbol.kind) === "list_resource",
          ),
        },
      ].filter((section) => section.items.length > 0),
    }));
  return [...standalone, ...services];
}

export function documentSectionKey(group: string, section: string): string {
  return `${group}::${section}`;
}

export function providerServiceCategory(name: string): string {
  const normalized = name
    .toLowerCase()
    .replace(/^azurerm_/, "")
    .replaceAll("-", "_");
  const categories: Array<[RegExp, string]> = [
    [/^(resource_group|subscription|resource_provider)/, "Base"],
    [/^(aad_b2c|aadb2c)/, "AAD B2C"],
    [/^(api_management)/, "API Management"],
    [/^(active_directory_domain)/, "Active Directory Domain Services"],
    [/^(advisor)/, "Advisor"],
    [/^(analysis_services)/, "Analysis Services"],
    [/^(app_configuration)/, "App Configuration"],
    [
      /^(app_service|function_app|service_plan|static_web_app)/,
      "App Service (Web Apps)",
    ],
    [/^(application_insights)/, "Application Insights"],
    [/^(arc_|kubernetes_flux)/, "ArcKubernetes"],
    [/^(authorization|role_|pim_|lighthouse)/, "Authorization"],
    [/^(automation)/, "Automation"],
    [/^(batch)/, "Batch"],
    [/^(billing)/, "Billing"],
    [/^(bot_)/, "Bot"],
    [/^(cdn_|frontdoor)/, "CDN"],
    [/^(chaos_)/, "Chaos Studio"],
    [/^(cognitive_|ai_services)/, "Cognitive Services"],
    [/^(communication_)/, "Communication"],
    [
      /^(linux_virtual_machine|windows_virtual_machine|virtual_machine|managed_disk|snapshot|image|gallery_)/,
      "Compute",
    ],
    [/^(container_|kubernetes_|log_analytics_solution)/, "Container"],
    [/^(cosmosdb_)/, "CosmosDB (DocumentDB)"],
    [/^(cost_management_)/, "Cost Management"],
    [/^(custom_provider)/, "Custom Providers"],
    [/^(dashboard)/, "Dashboard"],
    [/^(data_explorer|kusto_)/, "Data Explorer"],
    [/^(data_factory)/, "Data Factory"],
    [/^(data_share)/, "Data Share"],
    [/^(database_migration)/, "Database Migration"],
    [/^(mssql_|mysql_|postgresql_|mariadb_)/, "Database"],
    [/^(databricks_)/, "Databricks"],
    [/^(desktop_virtualization)/, "Desktop Virtualization"],
    [/^(dev_center|dev_test)/, "Dev Center"],
    [/^(digital_twins)/, "Digital Twins"],
    [/^(dns_|private_dns)/, "DNS"],
    [/^(eventgrid_|eventhub_|servicebus_|relay_)/, "Messaging"],
    [/^(healthcare_)/, "Healthcare"],
    [/^(iot_|iothub_)/, "IoT Hub"],
    [/^(key_vault)/, "Key Vault"],
    [/^(load_test)/, "Load Test"],
    [/^(log_analytics)/, "Log Analytics"],
    [/^(logic_app)/, "Logic App"],
    [/^(machine_learning)/, "Machine Learning"],
    [/^(maintenance_)/, "Maintenance"],
    [/^(management_group|management_lock)/, "Management"],
    [/^(maps_)/, "Maps"],
    [/^(monitor_|monitoring_|action_group)/, "Monitor"],
    [/^(netapp_)/, "NetApp"],
    [
      /^(virtual_network|subnet|network_|public_ip|private_endpoint|application_gateway|load_balancer|firewall|express_route|route_|traffic_manager|nat_gateway|bastion_)/,
      "Network",
    ],
    [/^(policy_)/, "Policy"],
    [/^(portal_)/, "Portal"],
    [/^(powerbi_)/, "PowerBI"],
    [/^(purview_)/, "Purview"],
    [/^(recovery_services|backup_)/, "Recovery Services"],
    [/^(redis_)/, "Redis"],
    [/^(search_)/, "Search"],
    [/^(security_center|sentinel_)/, "Security Center"],
    [/^(service_fabric)/, "Service Fabric"],
    [/^(spring_cloud)/, "Spring Cloud"],
    [/^(storage_|storageaccount)/, "Storage"],
    [/^(stream_analytics)/, "Stream Analytics"],
    [/^(synapse_)/, "Synapse"],
    [/^(template_|resource_deployment)/, "Template"],
  ];
  return (
    categories.find(([pattern]) => pattern.test(normalized))?.[1] ?? "Other"
  );
}

export function normalizeSymbolKind(kind: string): string {
  return kind
    .trim()
    .toLowerCase()
    .replaceAll(/[\s-]+/g, "_");
}

export function symbolKindLabel(kind: string): string {
  return normalizeSymbolKind(kind).split("_").map(capitalize).join(" ");
}

export function displayProviderSymbolName(
  packageName: string,
  symbol: PackageSymbol,
): string {
  const kind = normalizeSymbolKind(symbol.kind);
  if (
    ["resource", "data_source", "datasource"].includes(kind) &&
    !symbol.name.startsWith(`${packageName}_`)
  ) {
    return `${packageName}_${symbol.name}`;
  }
  return symbol.name;
}

export function formatDefaultValue(value: unknown): string {
  if (value === undefined) return "Unknown";
  if (typeof value === "string") return value || '""';
  if (value === null) return "null";
  if (
    typeof value === "number" ||
    typeof value === "boolean" ||
    typeof value === "bigint"
  ) {
    return String(value);
  }
  try {
    return JSON.stringify(value);
  } catch {
    return `Unserializable ${typeof value}`;
  }
}

export function cleanSymbolDescription(value?: string): string {
  const description = value?.trim();
  if (
    !hasText(description) ||
    /^<<[A-Z_]+$/.test(description) ||
    /^(?:optional|list|map|set|object)\s*\(/.test(description)
  ) {
    return "No description published.";
  }
  return description;
}

export function extractMarkdownHeadings(markdown: string) {
  return markdown
    .split(/\r?\n/)
    .map((line) => /^(#{1,3})\s+(.+?)\s*#*\s*$/.exec(line))
    .filter((match): match is RegExpExecArray => match !== null)
    .map((match) => {
      const markers = match[1] ?? "";
      const title = (match[2] ?? "").replaceAll(/[*_`[\]]/g, "").trim();
      return { level: markers.length, title, id: slugText(title) };
    });
}

export function slugText(value: unknown): string {
  return String(value)
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/g, "-")
    .replaceAll(/(^-|-$)/g, "");
}

export function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

export function formatCalendarDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en-US", {
    month: "long",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  }).format(date);
}

export function safeExternalUrl(value?: string): string | undefined {
  if (!hasText(value)) return undefined;
  try {
    const url = new URL(value);
    if (
      !["https:", "http:"].includes(url.protocol) ||
      url.hostname.endsWith(".invalid")
    ) {
      return undefined;
    }
    return url.toString();
  } catch {
    return undefined;
  }
}

export function dependencyDescription(symbol: PackageSymbol): string {
  const kind = normalizeSymbolKind(symbol.kind);
  if (symbol.type === "provider" || kind.includes("provider"))
    return `Provider requirement for ${symbol.source ?? symbol.description ?? symbol.name}.`;
  if (symbol.type === "module" || kind.includes("module"))
    return `Module dependency on ${symbol.source ?? symbol.description ?? symbol.name}.`;
  return symbol.description ?? "External dependency declared by this module.";
}

export function dependencyKind(symbol: PackageSymbol): string {
  if (hasText(symbol.type)) return capitalize(symbol.type);
  const kind = normalizeSymbolKind(symbol.kind);
  if (kind.includes("provider")) return "Provider";
  if (kind.includes("module")) return "Module";
  return "Dependency";
}

export function resourceProvider(symbol: PackageSymbol): string {
  if (hasText(symbol.provider)) return symbol.provider;
  const resourceType = symbol.type ?? symbol.name.split(".")[0] ?? symbol.name;
  const separator = resourceType.indexOf("_");
  return separator > 0 ? resourceType.slice(0, separator) : resourceType;
}

export function buildProviderConfigurationSnippet(item: PackageDetail): string {
  return `terraform {
  required_providers {
    ${item.name} = {
      source  = "${item.namespace}/${item.name}"
      version = "${item.version}"
    }
  }
}

provider "${item.name}" {
  # Configuration options
}`;
}

export function buildInstallSnippet(item: PackageDetail): string {
  if (item.kind === "provider") {
    return buildProviderConfigurationSnippet(item);
  }
  const requiredVariables = item.symbols.filter(
    (symbol) => symbol.kind === "input" && symbol.required === true,
  ).length;
  const source = `${item.namespace}/${item.name}/${item.target ?? item.provider}`;
  return `module "${item.name}" {
  source  = "${source}"
  version = "${item.version}"

  # insert the ${String(requiredVariables)} required variables here
}`;
}

export function buildModuleChildInstallSnippet(
  item: PackageDetail,
  childKind: ModuleChildKind,
  childName: string,
): string {
  const directory = childKind === "submodule" ? "modules" : "examples";
  const blockName = `${item.name}_${childKind}_${childName}`.replaceAll(
    "-",
    "_",
  );
  return `module "${blockName}" {
  source  = "${item.namespace}/${item.name}/${item.target ?? item.provider}//${directory}/${childName}"
  version = "${item.version}"
}`;
}

function dedupeModuleSymbols(symbols: PackageSymbol[]): PackageSymbol[] {
  const unique = new Map<string, PackageSymbol>();
  for (const symbol of symbols) {
    const kind = normalizeSymbolKind(symbol.kind);
    const key =
      kind === "dependency"
        ? `${kind}:${symbol.name}`
        : `${kind}:${symbol.name}:${symbol.path}`;
    if (!unique.has(key)) unique.set(key, symbol);
  }
  return [...unique.values()];
}
