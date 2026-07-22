import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import axe from "axe-core";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type * as ApiExports from "./api";
import { ApiError } from "./api";
import { AppShell } from "./components/AppShell";
import type * as HookExports from "./hooks";
import { RegistryProvider } from "./registry-provider";
import { LegacyPackageRedirect } from "./router";
import { CatalogPage } from "./routes/CatalogPage";
import { DocsPage } from "./routes/DocsPage";
import { HomePage } from "./routes/HomePage";
import type {
  CatalogPage as CatalogPageData,
  PackageSummary,
  RegistrySession,
} from "./types";

const hookMocks = vi.hoisted(() => ({
  useSession: vi.fn(),
  useCatalogPage: vi.fn(),
  useCatalogSuggestions: vi.fn(),
  useCatalogEvents: vi.fn(),
}));

const apiMocks = vi.hoisted(() => ({ logout: vi.fn() }));

vi.mock("./hooks", async (importOriginal) => {
  const actual = await importOriginal<typeof HookExports>();
  return {
    ...actual,
    useSession: hookMocks.useSession,
    useCatalogPage: hookMocks.useCatalogPage,
    useCatalogSuggestions: hookMocks.useCatalogSuggestions,
    useCatalogEvents: hookMocks.useCatalogEvents,
  };
});

vi.mock("./api", async (importOriginal) => {
  const actual = await importOriginal<typeof ApiExports>();
  return { ...actual, logout: apiMocks.logout };
});

const session: RegistrySession = {
  subject: "user-1",
  displayName: "Ada Lovelace",
  email: "ada@example.test",
  roles: ["registry-user"],
  apms: [
    { id: "APM0000001", name: "Cloud Platform" },
    { id: "APM0000002", name: "Payments" },
  ],
  csrfToken: "csrf",
  admin: false,
};

const provider: PackageSummary = {
  kind: "provider",
  namespace: "hashicorp",
  name: "aws",
  provider: "aws",
  version: "6.8.0",
  description: "AWS infrastructure provider",
  lifecycle: "approved",
  approval: "approved",
  risk: "low",
  verified: true,
  updatedAt: "2026-07-20T12:00:00Z",
  owner: "Cloud Platform",
  apmIds: ["APM0000001"],
};

const modulePackage: PackageSummary = {
  ...provider,
  kind: "module",
  namespace: "platform",
  name: "vpc",
  target: "aws",
  version: "2.4.1",
  description: "Governed VPC module",
};

function queryResult(data: CatalogPageData) {
  return {
    data,
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  };
}

function sessionResult(overrides: Record<string, unknown> = {}) {
  return {
    data: session,
    isPending: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
    ...overrides,
  };
}

function renderShell(initialEntry = "/") {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<div>Authenticated outlet</div>} />
          <Route path="docs" element={<DocsPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

function renderWithRegistry(
  node: React.ReactNode,
  initialEntry = "/",
  registrySession = session,
) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <RegistryProvider session={registrySession}>{node}</RegistryProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  localStorage.clear();
  hookMocks.useSession.mockReturnValue(sessionResult());
  hookMocks.useCatalogPage.mockImplementation((query: { kind?: string }) =>
    queryResult({
      items: query.kind === "module" ? [modulePackage] : [provider],
      total: 1,
    }),
  );
  hookMocks.useCatalogSuggestions.mockReturnValue(
    queryResult({ items: [], total: 0 }),
  );
  apiMocks.logout.mockResolvedValue("/signed-out");
});

afterEach(() => {
  vi.clearAllMocks();
  vi.unstubAllGlobals();
});

describe("application composition", () => {
  it("renders loading, session-expired, identity-error, and no-access states", () => {
    hookMocks.useSession.mockReturnValueOnce(
      sessionResult({ data: undefined, isPending: true }),
    );
    const loading = renderShell();
    expect(
      screen.getByRole("heading", { name: "Loading your registry" }),
    ).toBeVisible();
    loading.unmount();

    hookMocks.useSession.mockReturnValueOnce(
      sessionResult({
        data: undefined,
        isError: true,
        error: new ApiError("Expired", 401),
      }),
    );
    const expired = renderShell();
    expect(
      screen.getByRole("heading", { name: "Your session has expired" }),
    ).toBeVisible();
    expired.unmount();

    hookMocks.useSession.mockReturnValueOnce(
      sessionResult({
        data: undefined,
        isError: true,
        error: new ApiError("Graph unavailable", 503, "identity_unavailable"),
      }),
    );
    const identityError = renderShell();
    expect(
      screen.getByRole("heading", { name: "Identity service is unavailable" }),
    ).toBeVisible();
    identityError.unmount();

    hookMocks.useSession.mockReturnValueOnce(
      sessionResult({ data: { ...session, apms: [] } }),
    );
    renderShell();
    expect(
      screen.getByRole("heading", { name: "No registry access yet" }),
    ).toBeVisible();
  });

  it("renders the authenticated shell, mobile navigation, and logout behavior", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("location", { assign: vi.fn() });
    const { container } = renderShell();

    expect(screen.getByText("Authenticated outlet")).toBeVisible();
    await user.selectOptions(
      screen.getByRole("combobox", { name: "Access context" }),
      "APM0000002",
    );
    expect(localStorage.getItem("registry.apm.user-1")).toBe("APM0000002");
    await user.click(
      screen.getByRole("button", { name: "Switch to dark mode" }),
    );
    expect(document.documentElement.dataset["theme"]).toBe("dark");
    await user.click(screen.getByRole("button", { name: "Browse" }));
    expect(screen.getByRole("menuitem", { name: /Providers/i })).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Browse" }));
    await user.click(screen.getByRole("button", { name: "Open menu" }));
    expect(
      screen.getByRole("navigation", { name: "Mobile navigation" }),
    ).toBeVisible();

    await user.click(screen.getByRole("button", { name: /Ada Lovelace/i }));
    await user.click(screen.getByRole("menuitem", { name: /Sign out/i }));
    expect(apiMocks.logout).toHaveBeenCalledWith("csrf");

    const result = await axe.run(container, {
      rules: { "color-contrast": { enabled: false } },
    });
    expect(result.violations).toEqual([]);
  });

  it("renders truthful home counts and catalog content", () => {
    renderWithRegistry(<HomePage />);
    expect(screen.getAllByText("1")).toHaveLength(2);
    expect(
      screen.getByRole("link", { name: /AWSby Cloud Platform/i }),
    ).toBeVisible();
    expect(screen.getByRole("link", { name: /platform\/vpc/i })).toBeVisible();
    expect(screen.getByText(/APM0000001/)).toBeVisible();
  });

  it("renders home loading, empty, error, and administrator states truthfully", () => {
    hookMocks.useCatalogPage.mockReturnValueOnce({
      data: undefined,
      isPending: false,
      isError: true,
      refetch: vi.fn(),
    });
    hookMocks.useCatalogPage.mockReturnValueOnce({
      data: undefined,
      isPending: false,
      isError: true,
      refetch: vi.fn(),
    });
    const failed = renderWithRegistry(<HomePage />);
    expect(
      screen.getByRole("heading", {
        name: "The registry is temporarily unavailable",
      }),
    ).toBeVisible();
    failed.unmount();

    hookMocks.useCatalogPage.mockReturnValueOnce({
      data: undefined,
      isPending: true,
      isError: false,
      refetch: vi.fn(),
    });
    hookMocks.useCatalogPage.mockReturnValueOnce({
      data: undefined,
      isPending: true,
      isError: false,
      refetch: vi.fn(),
    });
    const loading = renderWithRegistry(<HomePage />);
    expect(
      loading.container.querySelectorAll(".source-card-skeleton"),
    ).toHaveLength(9);
    loading.unmount();

    hookMocks.useCatalogPage.mockReturnValue(
      queryResult({ items: [], total: 0 }),
    );
    const adminSession = { ...session, admin: true, apms: [] };
    renderWithRegistry(<HomePage />, "/", adminSession);
    expect(screen.getByText("Registry administrator")).toBeVisible();
    expect(
      screen.getAllByRole("heading", {
        name: "No packages match these filters",
      }),
    ).toHaveLength(2);
  });

  it("updates catalog filters, sort order, pagination, and responsive controls", async () => {
    const user = userEvent.setup();
    hookMocks.useCatalogPage.mockReturnValue(
      queryResult({
        items: [provider, modulePackage],
        total: 42,
        nextCursor: "page-2",
      }),
    );
    renderWithRegistry(<CatalogPage />, "/browse?q=cloud");

    expect(screen.getByText("42")).toBeVisible();
    await user.selectOptions(screen.getByLabelText("Sort by"), "name");
    await user.click(screen.getByRole("button", { name: "Filter Packages" }));
    await user.click(screen.getByRole("checkbox", { name: "Providers" }));
    await user.click(screen.getByRole("button", { name: /Next/i }));
    expect(screen.getByText(/Showing 21/)).toBeVisible();
  });

  it("renders catalog loading, API failure, and empty results", () => {
    hookMocks.useCatalogPage.mockReturnValueOnce({
      data: undefined,
      isPending: true,
      isError: false,
      refetch: vi.fn(),
    });
    const loading = renderWithRegistry(<CatalogPage />, "/browse");
    expect(loading.container.querySelector(".catalog-loading")).not.toBeNull();
    loading.unmount();

    hookMocks.useCatalogPage.mockReturnValueOnce({
      data: undefined,
      isPending: false,
      isError: true,
      refetch: vi.fn(),
    });
    const failed = renderWithRegistry(<CatalogPage />, "/providers");
    expect(
      screen.getByRole("heading", {
        name: "The registry is temporarily unavailable",
      }),
    ).toBeVisible();
    failed.unmount();

    hookMocks.useCatalogPage.mockReturnValueOnce(
      queryResult({ items: [], total: 0 }),
    );
    renderWithRegistry(<CatalogPage />, "/modules");
    expect(
      screen.getByRole("heading", { name: "No packages match these filters" }),
    ).toBeVisible();
  });

  it("keeps documentation reachable from a direct route", () => {
    renderShell("/docs");
    expect(
      screen.getByRole("heading", {
        name: "Use approved infrastructure with confidence",
      }),
    ).toBeVisible();
    expect(
      screen.getByRole("link", { name: "Getting started" }),
    ).toHaveAttribute("href", "#getting-started");
  });

  it("preserves provider and module deep links through canonical redirects", () => {
    function LocationOutput() {
      const location = useLocation();
      return (
        <output>{`${location.pathname}${location.search}${location.hash}`}</output>
      );
    }

    const providerRedirect = render(
      <MemoryRouter
        initialEntries={["/provider/hashicorp/aws/6.8.0?tab=docs#usage"]}
      >
        <Routes>
          <Route
            path="/provider/:namespace/:name/:version"
            element={<LegacyPackageRedirect kind="provider" />}
          />
          <Route path="*" element={<LocationOutput />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByRole("status")).toHaveTextContent(
      "/providers/hashicorp/aws/6.8.0?tab=docs#usage",
    );
    providerRedirect.unmount();

    render(
      <MemoryRouter initialEntries={["/module/platform/vpc/aws/2.4.1"]}>
        <Routes>
          <Route
            path="/module/:namespace/:name/:target/:version"
            element={<LegacyPackageRedirect kind="module" />}
          />
          <Route path="*" element={<LocationOutput />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByRole("status")).toHaveTextContent(
      "/modules/platform/vpc/aws/2.4.1",
    );
  });
});
