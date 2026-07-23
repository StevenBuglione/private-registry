import { describe, expect, it } from "vitest";
import type { PackageDetail, PackageSymbol } from "../../types";
import {
  buildInstallSnippet,
  buildModuleChildInstallSnippet,
  buildProviderConfigurationSnippet,
  capitalize,
  cleanSymbolDescription,
  dependencyDescription,
  dependencyKind,
  displayProviderSymbolName,
  documentSectionKey,
  extractMarkdownHeadings,
  formatCalendarDate,
  formatDefaultValue,
  formatDownloadCount,
  hasRootModuleConfiguration,
  moduleChildHref,
  moduleRootHref,
  moduleTabCount,
  namespaceHref,
  normalizeSymbolKind,
  providerDocumentGroups,
  providerLatestHref,
  providerServiceCategory,
  resourceProvider,
  safeExternalUrl,
  shortExternalUrl,
  sourceChildUrl,
  symbolKindLabel,
  symbolsForModuleTab,
  symbolsForModuleView,
} from "./model";

const symbol = (
  kind: string,
  name: string,
  path = "main.tf",
  extra: Partial<PackageSymbol> = {},
): PackageSymbol => ({ kind, name, path, ...extra });

const detail = (
  kind: PackageDetail["kind"],
  symbols: PackageSymbol[] = [],
): PackageDetail => ({
  kind,
  registryTier: kind === "provider" ? "official" : "community",
  namespace: "platform team",
  name: kind === "provider" ? "azurerm" : "network-module",
  target: kind === "module" ? "azurerm" : undefined,
  version: "1.2.3",
  description: "Managed package",
  provider: "azurerm",
  verified: true,
  updatedAt: "2026-01-01T00:00:00Z",
  versions: ["1.2.3"],
  symbols,
  examples: [],
  submodules: [],
  downloadStatisticsByVersion: {},
});

describe("package detail model", () => {
  it("formats counts, dates, labels, and default values", () => {
    expect(formatDownloadCount(undefined)).toBe("—");
    expect(formatDownloadCount(12_345)).toBe("12,345");
    expect(formatCalendarDate("2026-01-02T00:00:00Z")).toBe("January 2, 2026");
    expect(formatCalendarDate("not-a-date")).toBe("not-a-date");
    expect(normalizeSymbolKind(" Data-Source ")).toBe("data_source");
    expect(symbolKindLabel("data_source")).toBe("Data Source");
    expect(capitalize("provider")).toBe("Provider");
    expect(formatDefaultValue(undefined)).toBe("Unknown");
    expect(formatDefaultValue("")).toBe('""');
    expect(formatDefaultValue("eastus")).toBe("eastus");
    expect(formatDefaultValue(null)).toBe("null");
    expect(formatDefaultValue(4)).toBe("4");
    expect(formatDefaultValue(false)).toBe("false");
    expect(formatDefaultValue(BigInt(7))).toBe("7");
    expect(formatDefaultValue({ enabled: true })).toBe('{"enabled":true}');
    const circular: { self?: unknown } = {};
    circular.self = circular;
    expect(formatDefaultValue(circular)).toBe("Unserializable object");
  });

  it("creates safe display and source URLs", () => {
    expect(shortExternalUrl("https://github.com/hashicorp/terraform/")).toBe(
      "github.com/hashicorp/terraform",
    );
    expect(shortExternalUrl("not a url")).toBe("not a url");
    expect(
      sourceChildUrl(
        "https://github.com/hashicorp/terraform-azurerm",
        "v4.0.0",
        "examples/complete example",
      ),
    ).toBe(
      "https://github.com/hashicorp/terraform-azurerm/tree/v4.0.0/examples/complete%20example",
    );
    expect(sourceChildUrl(undefined, "v1", "examples/basic")).toBeUndefined();
    expect(
      sourceChildUrl("https://gitlab.com/team/repo", "v1", "examples/basic"),
    ).toBeUndefined();
    expect(sourceChildUrl("not a url", "v1", "examples/basic")).toBeUndefined();
    expect(safeExternalUrl("https://example.com/docs")).toBe(
      "https://example.com/docs",
    );
    expect(safeExternalUrl("ftp://example.com/file")).toBeUndefined();
    expect(safeExternalUrl("https://catalog.invalid/docs")).toBeUndefined();
    expect(safeExternalUrl("not a url")).toBeUndefined();
    expect(safeExternalUrl(" ")).toBeUndefined();
  });

  it("constructs canonical package navigation", () => {
    const module = detail("module");
    const provider = detail("provider");
    expect(moduleRootHref(module, "2.0.0-beta 1")).toBe(
      "/modules/platform%20team/network-module/azurerm/2.0.0-beta%201",
    );
    expect(moduleChildHref(module, "1.2.3", "examples", "complete app")).toBe(
      "/modules/platform%20team/network-module/azurerm/1.2.3/examples/complete%20app",
    );
    expect(providerLatestHref(provider)).toBe(
      "/providers/platform%20team/azurerm",
    );
    expect(namespaceHref("platform team")).toBe("/namespaces/platform%20team");
  });

  it("selects and de-duplicates module symbols by route and tab", () => {
    const symbols = [
      symbol("input", "location"),
      symbol("output", "id"),
      symbol("resource", "azurerm_vnet", "main.tf"),
      symbol("dependency", "azurerm", "versions.tf"),
      symbol("dependency", "azurerm", "modules/spoke/versions.tf"),
      symbol("input", "cidr", "modules/spoke/variables.tf"),
      symbol("resource", "azurerm_subnet", "modules/spoke/main.tf"),
      symbol("input", "demo", "examples/basic/variables.tf"),
      symbol("submodule", "spoke", "modules/spoke"),
    ];

    expect(
      symbolsForModuleView(symbols, "submodule", "spoke").map(
        (item) => item.name,
      ),
    ).toEqual(["azurerm", "cidr", "azurerm_subnet"]);
    expect(
      symbolsForModuleView(symbols, "example", "basic").map(
        (item) => item.name,
      ),
    ).toEqual(["demo"]);
    expect(
      symbolsForModuleView(symbols, undefined, undefined).map(
        (item) => item.name,
      ),
    ).toEqual(["location", "id", "azurerm_vnet", "azurerm", "azurerm_subnet"]);
    expect(hasRootModuleConfiguration(symbols)).toBe(true);
    expect(
      hasRootModuleConfiguration([
        symbol("input", "cidr", "modules/spoke/variables.tf"),
        symbol("submodule", "spoke", "modules/spoke"),
      ]),
    ).toBe(false);
    expect(symbolsForModuleTab(symbols, "inputs")).toHaveLength(3);
    expect(symbolsForModuleTab(symbols, "outputs")).toHaveLength(1);
    expect(symbolsForModuleTab(symbols, "dependencies")).toHaveLength(2);
    expect(symbolsForModuleTab(symbols, "resources")).toHaveLength(2);
    expect(symbolsForModuleTab(symbols, "unknown")).toEqual([]);
    expect(moduleTabCount(symbols, "outputs")).toBe(1);
  });

  it("groups provider documents into registry-compatible sections", () => {
    const symbols = [
      symbol("guide", "authentication", "guides/auth.md"),
      symbol("function", "parse_resource_id", "functions/parse.md"),
      symbol("resource", "azurerm_storage_account"),
      symbol("data-source", "azurerm_storage_account"),
      symbol("list resource", "azurerm_storage_accounts"),
      symbol("resource", "azurerm_virtual_network"),
      symbol("resource", "azurerm_unclassified_widget"),
    ];

    const groups = providerDocumentGroups(symbols, "");
    expect(groups.map((group) => group.label)).toEqual([
      "Guides",
      "Functions",
      "Network",
      "Other",
      "Storage",
    ]);
    expect(
      groups
        .find((group) => group.label === "Storage")
        ?.sections.map((section) => section.label),
    ).toEqual(["Resources", "Data Sources", "List Resources"]);
    expect(providerDocumentGroups(symbols, "virtual")).toHaveLength(1);
    expect(providerDocumentGroups(symbols, "missing")).toEqual([]);
    expect(documentSectionKey("Storage", "Resources")).toBe(
      "Storage::Resources",
    );
  });

  it("classifies representative provider services", () => {
    expect(providerServiceCategory("azurerm_resource_group")).toBe("Base");
    expect(providerServiceCategory("azurerm_linux_virtual_machine")).toBe(
      "Compute",
    );
    expect(providerServiceCategory("azurerm_key-vault")).toBe("Key Vault");
    expect(providerServiceCategory("azurerm_storage_account")).toBe("Storage");
    expect(providerServiceCategory("custom_widget")).toBe("Other");
  });

  it("cleans descriptions and extracts a sanitized table of contents", () => {
    expect(cleanSymbolDescription()).toBe("No description published.");
    expect(cleanSymbolDescription("  ")).toBe("No description published.");
    expect(cleanSymbolDescription("<<EOT")).toBe("No description published.");
    expect(cleanSymbolDescription("optional(string)")).toBe(
      "No description published.",
    );
    expect(cleanSymbolDescription("  A useful input. ")).toBe(
      "A useful input.",
    );
    expect(
      extractMarkdownHeadings(
        "# **Overview**\ntext\n## `Usage` ##\n#### Not included",
      ),
    ).toEqual([
      { level: 1, title: "Overview", id: "overview" },
      { level: 2, title: "Usage", id: "usage" },
    ]);
  });

  it("derives provider symbol names and module dependency details", () => {
    expect(
      displayProviderSymbolName(
        "azurerm",
        symbol("resource", "resource_group"),
      ),
    ).toBe("azurerm_resource_group");
    expect(
      displayProviderSymbolName(
        "azurerm",
        symbol("data_source", "azurerm_client_config"),
      ),
    ).toBe("azurerm_client_config");
    expect(
      displayProviderSymbolName("azurerm", symbol("guide", "authentication")),
    ).toBe("authentication");

    const provider = symbol("provider requirement", "azurerm", "versions.tf", {
      source: "hashicorp/azurerm",
      type: "provider",
    });
    const module = symbol("module_dependency", "network", "main.tf", {
      description: "company/network/azurerm",
      type: "module",
    });
    const generic = symbol("dependency", "external", "main.tf");
    expect(dependencyDescription(provider)).toContain("hashicorp/azurerm");
    expect(dependencyDescription(module)).toContain("company/network/azurerm");
    expect(dependencyDescription(generic)).toBe(
      "External dependency declared by this module.",
    );
    expect(dependencyKind(provider)).toBe("Provider");
    expect(dependencyKind(module)).toBe("Module");
    expect(dependencyKind(generic)).toBe("Dependency");
    expect(
      resourceProvider(
        symbol("resource", "network.vnet", "main.tf", {
          provider: "azurerm",
        }),
      ),
    ).toBe("azurerm");
    expect(resourceProvider(symbol("resource", "aws_vpc"))).toBe("aws");
    expect(resourceProvider(symbol("resource", "random"))).toBe("random");
  });

  it("builds provider, module, and child installation snippets", () => {
    const provider = detail("provider");
    const module = detail("module", [
      symbol("input", "location", "variables.tf", { required: true }),
      symbol("input", "name", "variables.tf", { required: true }),
      symbol("input", "tags", "variables.tf", { required: false }),
    ]);
    expect(buildProviderConfigurationSnippet(provider)).toContain(
      'source  = "platform team/azurerm"',
    );
    expect(buildInstallSnippet(provider)).toContain('provider "azurerm"');
    expect(buildInstallSnippet(module)).toContain(
      "# insert the 2 required variables here",
    );
    expect(
      buildModuleChildInstallSnippet(module, "submodule", "spoke-net"),
    ).toContain('module "network_module_submodule_spoke_net"');
    expect(
      buildModuleChildInstallSnippet(module, "example", "complete"),
    ).toContain("//examples/complete");
  });
});
