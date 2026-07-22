import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import axe from "axe-core";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RegistryProvider } from "../registry-provider";
import type { PackageSummary, RegistrySession } from "../types";
import { SearchBox } from "./SearchBox";

const packages: PackageSummary[] = [
  {
    kind: "provider",
    namespace: "platform",
    name: "aws",
    version: "6.5.0",
    description: "Approved AWS provider",
    provider: "AWS",
    owner: "Platform Engineering",
    approval: "approved",
    lifecycle: "approved",
    risk: "low",
    verified: true,
    updatedAt: "2026-07-21T12:00:00Z",
    apmIds: ["APM0001042"],
  },
  {
    kind: "module",
    namespace: "platform",
    name: "vpc",
    target: "aws",
    version: "1.4.0",
    description: "Approved network module",
    provider: "AWS",
    owner: "Platform Engineering",
    approval: "approved",
    lifecycle: "approved",
    risk: "low",
    verified: true,
    updatedAt: "2026-07-21T12:00:00Z",
    apmIds: ["APM0001042"],
  },
];

vi.mock("../hooks", () => ({
  useCatalogSuggestions: () => ({
    data: {
      items: packages,
      total: 2,
    },
    isPending: false,
  }),
}));

const session: RegistrySession = {
  subject: "user-1",
  displayName: "Alex Morgan",
  email: "alex@example.test",
  roles: [],
  apms: [{ id: "APM0001042", name: "Platform Engineering" }],
  csrfToken: "csrf-value",
  admin: false,
};

describe("SearchBox", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("shows grouped authorized provider and module suggestions", async () => {
    const user = userEvent.setup();
    const { container } = render(
      <MemoryRouter>
        <RegistryProvider session={session}>
          <SearchBox compact />
        </RegistryProvider>
      </MemoryRouter>,
    );

    await user.type(
      screen.getByRole("textbox", {
        name: "Search approved providers and modules",
      }),
      "aws",
    );

    expect(screen.getByText("Providers")).toBeInTheDocument();
    expect(screen.getByText("Modules")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /platform \/ aws/i }),
    ).toHaveAttribute("href", "/providers/platform/aws/6.5.0");
    expect(
      screen.getByRole("link", { name: /platform \/ vpc/i }),
    ).toHaveAttribute("href", "/modules/platform/vpc/aws/1.4.0");

    const result = await axe.run(container, {
      rules: { "color-contrast": { enabled: false } },
    });
    expect(result.violations).toEqual([]);
  });

  it("submits a normalized query through the explicit search contract", async () => {
    const user = userEvent.setup();
    const onSearch = vi.fn();
    render(
      <MemoryRouter>
        <RegistryProvider session={session}>
          <SearchBox initialValue="  aws  " onSearch={onSearch} />
        </RegistryProvider>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole("button", { name: "Search" }));
    expect(onSearch).toHaveBeenCalledWith("aws");
  });
});
