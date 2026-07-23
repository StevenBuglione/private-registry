import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import axe from "axe-core";
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RegistryProvider } from "../registry-provider";
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
  examples: [{ name: "complete", path: "examples/complete" }],
  submodules: [{ name: "network", path: "modules/network" }],
  description: "VPC module.",
  provider: "aws",
  verified: true,
  updatedAt: "2026-07-22T12:00:00Z",
  publishedAt: "2025-04-02T00:00:00Z",
  sourceRepository: "https://github.com/platform/terraform-aws-vpc",
  sourceTag: "v2.4.1",
  downloadStatisticsByVersion: {
    "2.4.1": {
      allTime: 314_159,
      week: 2_718,
      month: 8_154,
      year: 81_540,
      observedAt: "2026-07-22T12:00:00Z",
    },
  },
  downloadStatistics: {
    allTime: 552_494,
    week: 13_101,
    month: 39_613,
    year: 294_700,
    observedAt: "2026-07-22T12:00:00Z",
  },
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
    {
      kind: "submodule",
      name: "network",
      path: "modules/network",
    },
    {
      kind: "input",
      name: "subnet_count",
      description: "Number of subnets.",
      path: "modules/network/variables.tf",
      type: "number",
      defaultValue: "3",
      required: false,
    },
    {
      kind: "output",
      name: "subnet_ids",
      description: "Created subnet IDs.",
      path: "modules/network/outputs.tf",
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
  sourceRepository: "https://github.com/hashicorp/terraform-provider-aws",
  documentation: "# AWS Provider\n\nProvider overview.",
  artifactRepository: "iac-provider-release-local",
  artifactPath: "hashicorp/aws/6.8.0/provider.zip",
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
      "modules/network/README.md":
        "# Network submodule\n\nCreates the VPC network.",
      "examples/complete/README.md":
        "# Complete example\n\nRuns the complete configuration.",
    };
    return {
      data:
        documentPath !== undefined && documentPath.length > 0
          ? documents[documentPath]
          : initial,
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    };
  },
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
    <button
      type="button"
      onClick={() => {
        void navigate(-1);
      }}
    >
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

function renderModuleChildDetail(
  childKind: "submodule" | "example",
  initialEntry: string,
) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <RegistryProvider session={session}>
        <Routes>
          <Route
            path={`/modules/:namespace/:name/:target/:version/${childKind === "submodule" ? "submodules" : "examples"}/:moduleChild`}
            element={
              <PackageDetailPage kind="module" moduleChildKind={childKind} />
            }
          />
        </Routes>
      </RegistryProvider>
    </MemoryRouter>,
  );
}

describe("PackageDetailPage symbol-driven views", () => {
  it("links module breadcrumbs to browse, namespace, and current module routes", () => {
    renderDetail("module", "/modules/platform/vpc/aws/2.4.1");

    const breadcrumbs = within(
      screen.getByRole("navigation", { name: "Breadcrumb" }),
    );
    expect(breadcrumbs.getByRole("link", { name: "Modules" })).toHaveAttribute(
      "href",
      "/browse/modules",
    );
    expect(breadcrumbs.getByRole("link", { name: "platform" })).toHaveAttribute(
      "href",
      "/namespaces/platform",
    );
    expect(breadcrumbs.getByRole("link", { name: "vpc" })).toHaveAttribute(
      "href",
      "/modules/platform/vpc/aws/2.4.1",
    );
    expect(
      breadcrumbs.queryByRole("link", { name: "v2.4.1" }),
    ).not.toBeInTheDocument();
  });

  it("links provider breadcrumbs to browse, namespace, and latest provider routes", () => {
    renderDetail("provider", "/providers/hashicorp/aws/6.8.0");

    const breadcrumbs = within(
      screen.getByRole("navigation", { name: "Breadcrumb" }),
    );
    expect(
      breadcrumbs.getByRole("link", { name: "Providers" }),
    ).toHaveAttribute("href", "/browse/providers");
    expect(
      breadcrumbs.getByRole("link", { name: "hashicorp" }),
    ).toHaveAttribute("href", "/namespaces/hashicorp");
    expect(breadcrumbs.getByRole("link", { name: "aws" })).toHaveAttribute(
      "href",
      "/providers/hashicorp/aws",
    );
    expect(
      breadcrumbs.queryByRole("link", { name: "v6.8.0" }),
    ).not.toBeInTheDocument();
  });

  it("matches the provider overview information architecture with truthful private metadata", () => {
    renderDetail("provider", "/providers/hashicorp/aws/6.8.0");

    expect(
      screen.getByRole("heading", { name: "Top downloaded aws modules" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Showing 1 - 1 of 7 available modules"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Provider Downloads" }),
    ).toBeInTheDocument();
    expect(screen.getByText("This week:")).toBeInTheDocument();
    expect(screen.getAllByText("13,101")).toHaveLength(2);
    expect(screen.getByRole("link", { name: "View Source" })).toHaveAttribute(
      "href",
      "https://github.com/hashicorp/terraform-provider-aws",
    );
    const providerLinks = within(
      screen.getByRole("navigation", { name: "Provider links" }),
    );
    expect(providerLinks.getAllByRole("link")).toHaveLength(7);
    for (const name of [
      "Source Code",
      "Using providers",
      "Try HCP Terraform",
      "View tutorials",
      "Register for a workshop",
      "Post a forum question",
      "Report Issue",
    ]) {
      expect(providerLinks.getByRole("link", { name })).toBeInTheDocument();
    }
    expect(
      providerLinks.getByRole("link", { name: "Report Issue" }),
    ).toHaveAttribute(
      "href",
      "https://github.com/hashicorp/terraform-provider-aws/issues",
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

    const inputs = screen.getByRole("region", { name: "Optional Inputs" });
    expect(screen.getAllByText("552,494")).toHaveLength(2);
    expect(
      screen.getByRole("option", { name: "All versions" }),
    ).toBeInTheDocument();
    await user.selectOptions(
      screen.getByRole("combobox", { name: "Download statistics version" }),
      "2.4.1",
    );
    expect(screen.getByText("314,159")).toBeInTheDocument();
    expect(screen.getByText("April 2, 2025")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Examples/ }));
    expect(screen.getByRole("menuitem", { name: /complete/ })).toHaveAttribute(
      "href",
      "/modules/platform/vpc/aws/2.4.1/examples/complete",
    );
    expect(within(inputs).getByText("cidr_block")).toBeInTheDocument();
    expect(within(inputs).getByText("10.0.0.0/16")).toBeInTheDocument();
    expect(
      within(inputs).getByRole("button", { name: "Copy cidr_block" }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Outputs (1)" }));
    expect(
      within(screen.getByRole("region", { name: "Outputs" })).getByText(
        "vpc_id",
      ),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Dependencies (1)" }));
    expect(screen.getAllByText("hashicorp/aws")).toHaveLength(2);
    expect(
      screen.getByText("Provider requirement for hashicorp/aws."),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Resources (1)" }));
    const resource = screen.getByText("aws_vpc.this").closest("li");
    expect(resource).not.toBeNull();
    if (resource === null) throw new Error("Expected a resource list item");
    expect(within(resource).getByText("aws")).toBeInTheDocument();
    expect(
      within(resource).getByText("resources/aws_vpc.this"),
    ).toBeInTheDocument();

    const result = await axe.run(container, {
      rules: { "color-contrast": { enabled: false } },
    });
    expect(result.violations).toEqual([]);
  });

  it("routes module submodules and examples to Registry-rendered child pages", async () => {
    const user = userEvent.setup();
    renderDetail("module", "/modules/platform/vpc/aws/2.4.1");

    await user.click(screen.getByRole("button", { name: "Submodules" }));
    expect(screen.getByRole("menuitem", { name: "network" })).toHaveAttribute(
      "href",
      "/modules/platform/vpc/aws/2.4.1/submodules/network",
    );
    await user.click(screen.getByRole("button", { name: "Examples" }));
    expect(screen.getByRole("menuitem", { name: "complete" })).toHaveAttribute(
      "href",
      "/modules/platform/vpc/aws/2.4.1/examples/complete",
    );
  });

  it("renders submodule metadata and documentation without leaving Registry", () => {
    renderModuleChildDetail(
      "submodule",
      "/modules/platform/vpc/aws/2.4.1/submodules/network",
    );

    expect(
      screen.getByRole("heading", { name: "Submodule: network" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Return to module vpc" }),
    ).toHaveAttribute("href", "/modules/platform/vpc/aws/2.4.1");
    const breadcrumbs = within(
      screen.getByRole("navigation", { name: "Module submodule" }),
    );
    expect(breadcrumbs.getByRole("link", { name: "Modules" })).toHaveAttribute(
      "href",
      "/browse/modules",
    );
    expect(breadcrumbs.getByRole("link", { name: "platform" })).toHaveAttribute(
      "href",
      "/namespaces/platform",
    );
    expect(
      screen.getByRole("button", { name: "Change submodule" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Inputs (1)" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Network submodule" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/platform\/vpc\/aws\/\/modules\/network/),
    ).toBeInTheDocument();
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
    const vpcButtons = view.getAllByRole("button", { name: "aws_vpc" });
    const firstVpcButton = vpcButtons[0];
    expect(firstVpcButton).toBeDefined();
    if (firstVpcButton === undefined)
      throw new Error("Expected an aws_vpc button");
    await user.click(firstVpcButton);

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
