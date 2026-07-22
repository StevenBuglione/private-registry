import AxeBuilder from "@axe-core/playwright";
import { expect, type Page, test } from "@playwright/test";

const provider = {
  kind: "provider",
  namespace: "hashicorp",
  name: "azurerm",
  provider: "azurerm",
  version: "4.40.0",
  versions: ["4.40.0", "4.39.0"],
  description: "Manage approved Azure infrastructure.",
  lifecycle: "approved",
  approval: "approved",
  risk: "low",
  verified: true,
  updated_at: "2026-07-21T12:00:00Z",
  owner: "Cloud Platform",
  apm_ids: ["APM0000001"],
  artifact_repository: "iac-provider-release-local",
  artifact_path: "hashicorp/azurerm/4.40.0/provider.zip",
  documentation: "# AzureRM Provider\n\nUse approved Azure resources.",
  symbols: [
    {
      kind: "resource",
      name: "azurerm_resource_group",
      path: "resources/resource_group.md",
      description: "Manages an Azure resource group.",
    },
    {
      kind: "data-source",
      name: "azurerm_client_config",
      path: "data-sources/client_config.md",
      description: "Reads the active Azure client configuration.",
    },
  ],
  governance: {
    owner: "Cloud Platform",
    support: "#registry-support",
    approval: "approved",
    lifecycle: "approved",
    risk: "low",
    apm_ids: ["APM0000001"],
  },
};

const modulePackage = {
  kind: "module",
  namespace: "platform",
  name: "vnet",
  target: "azurerm",
  provider: "azurerm",
  version: "1.0.0",
  versions: ["1.0.0", "0.9.0"],
  description: "Creates a governed Azure virtual network.",
  lifecycle: "approved",
  approval: "approved",
  risk: "medium",
  verified: true,
  updated_at: "2026-07-20T12:00:00Z",
  owner: "Network Platform",
  apm_ids: ["APM0000001"],
  documentation: "# Virtual network module\n\nA governed Azure network.",
  symbols: [
    {
      kind: "input",
      name: "resource_group_name",
      path: "inputs/resource_group_name",
      description: "Resource group that owns the network.",
      type: "string",
      required: true,
    },
    {
      kind: "output",
      name: "virtual_network_id",
      path: "outputs/virtual_network_id",
      description: "The created virtual network ID.",
      type: "string",
    },
  ],
};

async function mockRegistry(page: Page): Promise<string[]> {
  const catalogRequests: string[] = [];
  await page.route("**/config/runtime.json", async (route) => {
    await route.fulfill({
      json: {
        apiBaseUrl: "/api/v1",
        environment: "browser-test",
        jfrogHostname: "artifactory.example.test",
      },
    });
  });
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/v1/auth/session") {
      await route.fulfill({
        json: {
          subject: "browser-user",
          display_name: "Browser User",
          email: "browser@example.test",
          roles: ["registry-user"],
          apm_entitlements: [
            { apm_id: "APM0000001", display_name: "Cloud Platform" },
          ],
          csrf_token: "browser-csrf",
        },
      });
      return;
    }

    if (url.pathname.endsWith("/documentation")) {
      const selected = url.searchParams.get("path");
      await route.fulfill({
        json: {
          markdown:
            selected === "resources/resource_group.md"
              ? "# azurerm_resource_group Resource\n\nManages an Azure resource group."
              : provider.documentation,
        },
      });
      return;
    }

    if (url.pathname.endsWith("/governance")) {
      await route.fulfill({ json: provider.governance });
      return;
    }

    if (url.pathname === "/api/v1/catalog/events") {
      await route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        body: "event: ready\ndata: {}\n\n",
      });
      return;
    }

    if (url.pathname === "/api/v1/catalog/packages") {
      catalogRequests.push(url.toString());
      const kind = url.searchParams.get("kind");
      const items =
        kind === "provider"
          ? [provider]
          : kind === "module"
            ? [modulePackage]
            : [provider, modulePackage];
      await route.fulfill({ json: { items, total: items.length } });
      return;
    }

    if (url.pathname.includes("/catalog/packages/provider/")) {
      catalogRequests.push(url.toString());
      await route.fulfill({ json: provider });
      return;
    }

    if (url.pathname.includes("/catalog/packages/module/")) {
      catalogRequests.push(url.toString());
      await route.fulfill({ json: modulePackage });
      return;
    }

    await route.fulfill({ status: 404, json: { message: "Not found" } });
  });
  return catalogRequests;
}

test.beforeEach(async ({ page }) => {
  await mockRegistry(page);
});

test("authorized home and theme are accessible at desktop and mobile sizes", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Registry" })).toBeVisible();
  await expect(page.getByRole("link", { name: /AzureRM/i })).toBeVisible();

  const desktopScan = await new AxeBuilder({ page }).analyze();
  expect(desktopScan.violations).toEqual([]);

  await page.getByRole("button", { name: "Switch to dark mode" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");

  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "Open menu" }).click();
  await expect(
    page.getByRole("navigation", { name: "Mobile navigation" }),
  ).toBeVisible();
  const mobileScan = await new AxeBuilder({ page }).analyze();
  expect(mobileScan.violations).toEqual([]);
});

test("provider resources match the canonical detail and documentation flow", async ({
  page,
}) => {
  await page.goto("/provider/hashicorp/azurerm/4.40.0");
  await expect(page).toHaveURL(/\/providers\/hashicorp\/azurerm\/4\.40\.0$/);
  await expect(
    page.getByRole("heading", { name: "azurerm", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Documentation" }).click();
  await expect(
    page.getByRole("textbox", { name: "Filter documentation" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Base" }).click();
  await page.getByRole("button", { name: "Resources" }).click();
  await page.getByRole("button", { name: "azurerm_resource_group" }).click();
  await expect(
    page.getByRole("heading", { name: "azurerm_resource_group Resource" }),
  ).toBeVisible();
});

test("module inputs and outputs remain discoverable", async ({ page }) => {
  await page.goto("/modules/platform/vnet/azurerm/1.0.0?tab=inputs");
  await expect(
    page.getByRole("region", { name: "Required Inputs" }),
  ).toContainText("resource_group_name");
  await page.getByRole("button", { name: "Outputs (1)" }).click();
  await expect(page.getByRole("region", { name: "Outputs" })).toContainText(
    "virtual_network_id",
  );
});

test("every catalog request carries the selected APM and no restricted metadata", async ({
  page,
}) => {
  const requests = await mockRegistry(page);
  await page.goto("/browse?q=azure");
  await expect(
    page.getByRole("heading", { name: /Results for/ }),
  ).toBeVisible();
  await expect(page.getByText("restricted-provider")).toHaveCount(0);
  expect(requests.length).toBeGreaterThan(0);
  for (const request of requests) {
    expect(new URL(request).searchParams.get("apm_id")).toBe("APM0000001");
  }
});
