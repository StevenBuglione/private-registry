import axe from "axe-core";
import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import type { PackageSummary } from "../types";
import { PackageTable } from "./PackageTable";

const item: PackageSummary = {
  kind: "provider",
  namespace: "hashicorp",
  name: "azurerm",
  version: "4.36.0",
  description: "Manage Microsoft Azure resources.",
  provider: "Azure",
  owner: "Cloud Platform",
  approval: "approved",
  lifecycle: "approved",
  risk: "low",
  verified: true,
  updatedAt: "2026-07-21T18:00:00Z",
  apmIds: ["APM0001042"],
};

describe("PackageTable", () => {
  it("renders an accessible authorized result table", async () => {
    const { container, getByRole } = render(
      <MemoryRouter>
        <PackageTable items={[item]} />
      </MemoryRouter>,
    );
    expect(getByRole("link", { name: "Open azurerm" })).toHaveAttribute(
      "href",
      "/providers/hashicorp/azurerm/4.36.0",
    );
    const result = await axe.run(container, {
      rules: { "color-contrast": { enabled: false } },
    });
    expect(result.violations).toEqual([]);
  });
});
