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

    if (url.pathname === "/api/v1/registry/homepage") {
      await route.fulfill({
        json: {
          notification_enabled: true,
          notification_title: "Your private Registry is ready",
          notification_message:
            "Browse approved providers and modules from every APM group you belong to.",
          featured_provider_ids: ["provider/hashicorp/azurerm"],
          updated_at: "2026-07-22T12:00:00Z",
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
  await expect(
    page.getByRole("link", { name: /Azure by HashiCorp/i }),
  ).toBeVisible();

  const desktopScan = await new AxeBuilder({ page }).analyze();
  expect(desktopScan.violations).toEqual([]);

  await page.getByRole("button", { name: /Browser User/ }).click();
  await page.getByRole("menuitem", { name: "Switch to dark mode" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");

  for (const viewport of [
    { width: 1024, height: 900 },
    { width: 768, height: 900 },
  ]) {
    await page.setViewportSize(viewport);
    const layoutWidth = await page.evaluate(() => ({
      client: document.documentElement.clientWidth,
      scroll: document.documentElement.scrollWidth,
    }));
    expect(layoutWidth.scroll).toBeLessThanOrEqual(layoutWidth.client);
    const responsiveScan = await new AxeBuilder({ page }).analyze();
    expect(responsiveScan.violations).toEqual([]);
  }

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

  await page.goto("/providers/hashicorp/azurerm/latest?tab=documentation");
  await page.getByRole("button", { name: "Base" }).click();
  await expect(page.getByRole("button", { name: "Resources" })).toBeVisible();
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

test("package breadcrumbs navigate through browse, publisher, and package routes", async ({
  page,
}) => {
  await page.goto("/modules/platform/vnet/azurerm/1.0.0");
  const moduleBreadcrumbs = page.getByRole("navigation", {
    name: "Breadcrumb",
  });
  await expect(
    moduleBreadcrumbs.getByRole("link", { name: "Modules" }),
  ).toHaveAttribute("href", "/browse/modules");
  await expect(
    moduleBreadcrumbs.getByRole("link", { name: "platform" }),
  ).toHaveAttribute("href", "/namespaces/platform");
  await expect(
    moduleBreadcrumbs.getByRole("link", { name: "vnet" }),
  ).toHaveAttribute("href", "/modules/platform/vnet/azurerm/1.0.0");
  await expect(
    moduleBreadcrumbs.getByRole("link", { name: "v1.0.0" }),
  ).toHaveCount(0);

  await moduleBreadcrumbs.getByRole("link", { name: "platform" }).click();
  await expect(page).toHaveURL(/\/namespaces\/platform$/);
  await expect(
    page.getByRole("heading", { name: "platform", exact: true }),
  ).toBeVisible();

  await page.goto("/providers/hashicorp/azurerm/4.40.0");
  const providerBreadcrumbs = page.getByRole("navigation", {
    name: "Breadcrumb",
  });
  await expect(
    providerBreadcrumbs.getByRole("link", { name: "Providers" }),
  ).toHaveAttribute("href", "/browse/providers");
  await expect(
    providerBreadcrumbs.getByRole("link", { name: "hashicorp" }),
  ).toHaveAttribute("href", "/namespaces/hashicorp");
  await expect(
    providerBreadcrumbs.getByRole("link", { name: "azurerm" }),
  ).toHaveAttribute("href", "/providers/hashicorp/azurerm");
  await expect(
    providerBreadcrumbs.getByRole("link", { name: "v4.40.0" }),
  ).toHaveCount(0);

  await providerBreadcrumbs.getByRole("link", { name: "azurerm" }).click();
  await expect(page).toHaveURL(/\/providers\/hashicorp\/azurerm$/);
  await expect(
    page.getByRole("heading", { name: "azurerm", exact: true }),
  ).toBeVisible();

  await page.goto("/providers/hashicorp/azurerm/4.40.0");
  await page
    .getByRole("navigation", { name: "Breadcrumb" })
    .getByRole("link", { name: "Providers" })
    .click();
  await expect(page).toHaveURL(/\/browse\/providers$/);
  await expect(
    page.getByRole("heading", { name: "Providers", exact: true }),
  ).toBeVisible();
});

test("module logos remain separated from module content", async ({ page }) => {
  await page.setViewportSize({ width: 1100, height: 1110 });
  await page.goto("/modules?provider=azurerm");

  const card = page.getByRole("link", { name: /platform \/ vnet/i });
  await expect(card).toBeVisible();
  const spacing = await card.evaluate((element) => {
    const icon = element.querySelector<HTMLElement>(".package-icon");
    const content = element.querySelector<HTMLElement>(".module-card-content");
    if (icon === null || content === null) {
      throw new Error("Expected module card icon and content");
    }
    const iconBounds = icon.getBoundingClientRect();
    const contentBounds = content.getBoundingClientRect();
    return {
      gap: contentBounds.left - iconBounds.right,
      iconWidth: iconBounds.width,
    };
  });

  expect(spacing.iconWidth).toBe(48);
  expect(spacing.gap).toBeGreaterThanOrEqual(16);
});

test("catalog requests aggregate server-side APM access without a client selector", async ({
  page,
}) => {
  const requests = await mockRegistry(page);
  await page.goto("/browse?q=azure");
  await expect(
    page.getByRole("heading", { name: /Results for/ }),
  ).toBeVisible();
  await expect(page.getByText("restricted-provider")).toHaveCount(0);
  await expect(
    page.getByRole("combobox", { name: "Access context" }),
  ).toHaveCount(0);
  expect(requests.length).toBeGreaterThan(0);
  for (const request of requests) {
    expect(new URL(request).searchParams.get("apm_id")).toBeNull();
  }
});
