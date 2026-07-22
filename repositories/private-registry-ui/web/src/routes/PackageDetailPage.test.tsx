import axe from "axe-core";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RegistryProvider } from "../registry-context";
import type { PackageDetail, PackageKind, RegistrySession } from "../types";
import { PackageDetailPage } from "./PackageDetailPage";

const documentationHook = vi.hoisted(() => vi.fn());

afterEach(() => {
  cleanup();
  documentationHook.mockClear();
});

const moduleDetail: PackageDetail = {
  kind: "module",
  namespace: "platform",
  name: "vpc",
  target: "aws",
  version: "2.4.1",
  versions: ["2.4.1"],
  description: "Approved VPC module.",
  provider: "aws",
  owner: "Cloud Platform",
  approval: "approved",
  lifecycle: "approved",
  risk: "low",
  verified: true,
  updatedAt: "2026-07-22T12:00:00Z",
  apmIds: ["APM0000001"],
  documentation: "# VPC Module\n\nModule readme.",
  symbols: [
    {
      kind: "input",
      name: "cidr_block",
      description: "IPv4 CIDR range.",
      path: "#inputs",
      type: "string",
      defaultValue: "10.0.0.0/16",
      required: false,
      sensitive: false,
    },
    {
      kind: "output",
      name: "vpc_id",
      description: "Created VPC ID.",
      path: "#outputs",
      type: "string",
      sensitive: false,
    },
    {
      kind: "provider_dependency",
      name: "hashicorp/aws",
      description: "AWS provider dependency.",
      path: "providers/hashicorp/aws",
      provider: "aws",
      source: "hashicorp/aws",
    },
    {
      kind: "resource",
      name: "aws_vpc.this",
      description: "Managed VPC.",
      path: "resources/aws_vpc.this",
      provider: "aws",
    },
  ],
};

const providerDetail: PackageDetail = {
  ...moduleDetail,
  kind: "provider",
  namespace: "hashicorp",
  name: "aws",
  target: undefined,
  version: "6.8.0",
  versions: ["6.8.0", "6.7.0"],
  description: "AWS infrastructure provider.",
  documentation: "# AWS Provider\n\nProvider overview.",
  artifactRepository: "iac-provider-release-local",
  artifactPath: "hashicorp/aws/6.8.0/provider.zip",
  governance: {
    owner: "Cloud Platform",
    support: "supported",
    approval: "approved",
    lifecycle: "approved",
    risk: "low",
    sourceRepository: "https://github.com/hashicorp/terraform-provider-aws",
    apmIds: ["APM0000001"],
  },
  symbols: [
    {
      kind: "guide",
      name: "Authentication",
      description: "Configure authentication.",
      path: "guides/authentication.md",
    },
    {
      kind: "resource",
      name: "aws_vpc",
      description: "Manages a VPC.",
      path: "resources/aws_vpc.md",
    },
    {
      kind: "datasource",
      name: "aws_vpc",
      description: "Reads a VPC.",
      path: "data-sources/aws_vpc.md",
    },
    {
      kind: "function",
      name: "arn_parse",
      description: "Parses an ARN.",
      path: "functions/arn_parse.md",
    },
  ],
};

vi.mock("../hooks", () => ({
  usePackage: (identity: { kind: PackageKind }) => ({
    data: identity.kind === "module" ? moduleDetail : providerDetail,
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  }),
  usePackageDocumentation: (
    _identity: unknown,
    initial?: string,
    documentPath?: string,
  ) => {
    documentationHook(documentPath);
    const documents: Record<string, string> = {
      "resources/aws_vpc.md":
        "# aws_vpc Resource\n\nManages an Amazon Virtual Private Cloud.\n\n-> **Note:** Deleting the VPC also deletes managed routes.",
      "data-sources/aws_vpc.md": "# aws_vpc Data Source\n\nReads a VPC.",
      "guides/authentication.md": "# Authentication\n\nConfigure credentials.",
      "functions/arn_parse.md": "# arn_parse Function\n\nParses an ARN.",
    };
    return {
      data: documentPath ? documents[documentPath] : initial,
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    };
  },
  usePackageGovernance: () => ({
    data: undefined,
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useCatalogPage: () => ({
    data: { items: [moduleDetail], total: 7 },
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  }),
}));

const session: RegistrySession = {
  subject: "user-1",
  displayName: "APM User",
  email: "user@example.test",
  roles: ["registry-user"],
  apms: [{ id: "APM0000001", name: "Cloud Platform" }],
  csrfToken: "csrf",
  admin: false,
};

function LocationProbe() {
  const location = useLocation();
  return (
    <output data-testid="location">
      {location.pathname}
      {location.search}
    </output>
  );
}

function BackButton() {
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => navigate(-1)}>
      Browser back
    </button>
  );
}

function renderDetail(kind: PackageKind, initialEntry: string) {
  const path =
    kind === "module"
      ? "/modules/:namespace/:name/:target/:version"
      : "/providers/:namespace/:name/:version";
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <RegistryProvider session={session}>
        <Routes>
          <Route
            path={path}
            element={
              <>
                <PackageDetailPage kind={kind} />
                <LocationProbe />
                <BackButton />
              </>
            }
          />
        </Routes>
      </RegistryProvider>
    </MemoryRouter>,
  );
}

describe("PackageDetailPage symbol-driven views", () => {
  it("matches the provider overview information architecture with truthful private metadata", () => {
    renderDetail("provider", "/providers/hashicorp/aws/6.8.0");

    expect(
      screen.getByRole("heading", { name: "Approved aws modules" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Showing 1 - 1 of 7 available modules"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Provider Versions" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View Source" })).toHaveAttribute(
      "href",
      "https://github.com/hashicorp/terraform-provider-aws",
    );
    expect(
      screen.getByText(/source\s*=\s*"hashicorp\/aws"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/iac-provider-release-local\/hashicorp\/aws\/6.8.0/),
    ).toBeInTheDocument();
  });

  it("renders populated module definition and inventory tabs accessibly", async () => {
    const user = userEvent.setup();
    const { container } = renderDetail(
      "module",
      "/modules/platform/vpc/aws/2.4.1?tab=inputs",
    );

    const inputs = screen.getByRole("table", {
      name: "Module input definitions",
    });
    expect(within(inputs).getByText("cidr_block")).toBeInTheDocument();
    expect(within(inputs).getByText("10.0.0.0/16")).toBeInTheDocument();
    expect(within(inputs).getAllByText("No")).toHaveLength(2);

    await user.click(screen.getByRole("button", { name: "Outputs (1)" }));
    expect(
      within(
        screen.getByRole("table", { name: "Module output definitions" }),
      ).getByText("vpc_id"),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Dependencies (1)" }));
    expect(screen.getAllByText("hashicorp/aws")).toHaveLength(2);
    expect(
      screen.getByText("Provider requirement for hashicorp/aws."),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Resources (1)" }));
    const resource = screen.getByText("aws_vpc.this").closest("li");
    expect(resource).not.toBeNull();
    expect(within(resource!).getByText("aws")).toBeInTheDocument();
    expect(
      within(resource!).getByText("resources/aws_vpc.this"),
    ).toBeInTheDocument();

    const result = await axe.run(container, {
      rules: { "color-contrast": { enabled: false } },
    });
    expect(result.violations).toEqual([]);
  });

  it("filters and loads selected provider documents with history navigation", async () => {
    const user = userEvent.setup();
    const { container } = renderDetail(
      "provider",
      "/providers/hashicorp/aws/6.8.0?tab=documentation",
    );
    const view = within(container);

    expect(
      view.getByRole("heading", { name: "AWS Provider" }),
    ).toBeInTheDocument();
    expect(
      within(
        view.getByRole("complementary", { name: "On this page" }),
      ).queryByRole("link", { name: "AWS Provider" }),
    ).not.toBeInTheDocument();
    expect(
      view.queryByRole("button", { name: "Authentication" }),
    ).not.toBeInTheDocument();
    await user.type(
      view.getByRole("textbox", { name: "Filter documentation" }),
      "vpc",
    );
    expect(
      view.queryByRole("button", { name: "arn_parse" }),
    ).not.toBeInTheDocument();
    await user.click(view.getAllByRole("button", { name: "aws_vpc" })[0]);

    expect(
      await view.findByRole("heading", { name: "aws_vpc Resource" }),
    ).toBeInTheDocument();
    expect(view.getByTestId("location")).toHaveTextContent(
      "doc=resources%2Faws_vpc.md",
    );
    expect(documentationHook).toHaveBeenCalledWith("resources/aws_vpc.md");
    expect(view.getByRole("note")).toHaveTextContent(
      "Deleting the VPC also deletes managed routes.",
    );

    await user.click(view.getByRole("button", { name: "Browser back" }));
    expect(
      await view.findByRole("heading", { name: "AWS Provider" }),
    ).toBeInTheDocument();

    await user.selectOptions(
      view.getByRole("combobox", { name: "Provider version" }),
      "6.7.0",
    );
    expect(view.getByTestId("location")).toHaveTextContent(
      "/providers/hashicorp/aws/6.7.0?tab=documentation",
    );

    const result = await axe.run(container, {
      rules: { "color-contrast": { enabled: false } },
    });
    expect(result.violations).toEqual([]);
  });
});
