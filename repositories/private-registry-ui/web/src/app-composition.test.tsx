import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import axe from "axe-core";
import {
  MemoryRouter,
  Navigate,
  Route,
  Routes,
  useLocation,
} from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";
import { AppShell } from "./components/AppShell";
import { RegistryProvider } from "./registry-provider";
import { LegacyPackageRedirect } from "./router";
import { AdminSettingsPage } from "./routes/AdminSettingsPage";
import { CatalogPage } from "./routes/CatalogPage";
import { HomePage } from "./routes/HomePage";
import type {
  CatalogPage as CatalogPageData,
  PackageSummary,
  RegistrySession,
} from "./types";

const hookMocks = vi.hoisted(() => ({
  useSession: vi.fn(),
  useCatalogPage: vi.fn(),
  useFeaturedPackages: vi.fn(),
  useCatalogSuggestions: vi.fn(),
  useCatalogEvents: vi.fn(),
  useHomepageSettings: vi.fn(),
  useUpdateHomepageSettings: vi.fn(),
  useAdminDashboard: vi.fn(),
  useTrafficReport: vi.fn(),
  useAdminOperations: vi.fn(),
  useAuditEvents: vi.fn(),
  useSyncCredentials: vi.fn(),
  useCreateSyncCredential: vi.fn(),
  useRevokeSyncCredential: vi.fn(),
}));

const apiMocks = vi.hoisted(() => ({
  logout: vi.fn(),
  recordPageView: vi.fn(),
}));

vi.mock("./hooks/auth", () => ({
  useSession: hookMocks.useSession,
}));

vi.mock("./hooks/catalog", () => ({
  useCatalogPage: hookMocks.useCatalogPage,
  useFeaturedPackages: hookMocks.useFeaturedPackages,
  useCatalogSuggestions: hookMocks.useCatalogSuggestions,
  useCatalogEvents: hookMocks.useCatalogEvents,
}));

vi.mock("./hooks/homepage", () => ({
  useHomepageSettings: hookMocks.useHomepageSettings,
  useUpdateHomepageSettings: hookMocks.useUpdateHomepageSettings,
}));

vi.mock("./hooks/admin", () => ({
  useAdminDashboard: hookMocks.useAdminDashboard,
  useTrafficReport: hookMocks.useTrafficReport,
  useAdminOperations: hookMocks.useAdminOperations,
  useAuditEvents: hookMocks.useAuditEvents,
  useSyncCredentials: hookMocks.useSyncCredentials,
  useCreateSyncCredential: hookMocks.useCreateSyncCredential,
  useRevokeSyncCredential: hookMocks.useRevokeSyncCredential,
}));

vi.mock("./api/auth", () => ({
  logout: apiMocks.logout,
}));

vi.mock("./api/analytics", () => ({
  recordPageView: apiMocks.recordPageView,
}));

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
  registryTier: "official",
  namespace: "hashicorp",
  name: "aws",
  provider: "aws",
  version: "6.8.0",
  description: "AWS infrastructure provider",
  verified: true,
  updatedAt: "2026-07-20T12:00:00Z",
};

const modulePackage: PackageSummary = {
  ...provider,
  kind: "module",
  registryTier: "community",
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
          <Route path="docs" element={<Navigate replace to="/" />} />
          <Route path="admin" element={<AdminSettingsPage />} />
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
  hookMocks.useFeaturedPackages.mockImplementation((ids: string[]) => ({
    items:
      ids.length === 0
        ? []
        : ids[0]?.startsWith("module/") === true
          ? [modulePackage]
          : [provider],
    isPending: false,
    isError: false,
  }));
  hookMocks.useHomepageSettings.mockReturnValue({
    data: {
      notificationEnabled: true,
      notificationTitle: "Registry notice",
      notificationMessage: "Terraform catalog content.",
      featuredProvidersEnabled: true,
      featuredModulesEnabled: true,
      featuredProviderIds: ["provider/hashicorp/aws"],
      featuredModuleIds: ["module/terraform-aws-modules/iam/aws"],
      updatedAt: "2026-07-22T12:00:00Z",
    },
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  });
  hookMocks.useUpdateHomepageSettings.mockReturnValue({
    mutateAsync: vi.fn(),
    isPending: false,
    isSuccess: false,
    error: null,
  });
  hookMocks.useAdminDashboard.mockReturnValue({
    data: {
      generatedAt: "2026-07-23T12:00:00Z",
      status: "healthy",
      workerEnabled: true,
      dependencies: { postgresql: "up", artifactory: "up" },
      catalog: {
        providers: 12,
        modules: 30,
        activeVersions: 84,
        documents: 240,
        downloads: 12400,
      },
      queue: {
        queued: 0,
        processing: 1,
        retry: 0,
        completed: 86,
        deadLetter: 0,
      },
      ingestion: {
        completed: 42,
        failed: 0,
        quarantined: 0,
        latencyP95Ms: 840,
        lastCompletedAt: "2026-07-23T11:58:00Z",
      },
      reconciliation: {
        id: "run-1",
        mode: "repair",
        scope: "all-ready-manifests",
        status: "completed",
        discrepancies: 2,
        repaired: 2,
        startedAt: "2026-07-23T11:00:00Z",
        completedAt: "2026-07-23T11:01:00Z",
      },
      databaseSizeBytes: 1048576,
    },
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  });
  hookMocks.useTrafficReport.mockReturnValue({
    data: {
      generatedAt: "2026-07-23T12:00:00Z",
      days: 30,
      summary: {
        pageViews: 1284,
        uniqueVisitors: 42,
        pageViewsToday: 86,
        visitorsToday: 18,
      },
      daily: [
        { day: "2026-07-22", pageViews: 62, uniqueVisitors: 14 },
        { day: "2026-07-23", pageViews: 86, uniqueVisitors: 18 },
      ],
      topRoutes: [
        {
          path: "/modules",
          pageViews: 630,
          uniqueVisitors: 37,
          lastViewedAt: "2026-07-23T11:59:00Z",
        },
      ],
      visitors: [
        {
          subject: "user-1",
          displayName: "Ada Lovelace",
          email: "ada@example.test",
          pageViews: 28,
          firstSeenAt: "2026-07-20T10:00:00Z",
          lastSeenAt: "2026-07-23T11:59:00Z",
          lastPath: "/modules",
        },
      ],
      recentAccess: [
        {
          subject: "user-1",
          displayName: "Ada Lovelace",
          email: "ada@example.test",
          path: "/modules",
          occurredAt: "2026-07-23T11:59:00Z",
        },
      ],
    },
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  });
  hookMocks.useAdminOperations.mockReturnValue({
    data: [],
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  });
  hookMocks.useAuditEvents.mockReturnValue({
    data: [],
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  });
  hookMocks.useSyncCredentials.mockReturnValue({
    data: [],
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  });
  hookMocks.useCreateSyncCredential.mockReturnValue({
    mutateAsync: vi.fn(),
    isPending: false,
    error: null,
  });
  hookMocks.useRevokeSyncCredential.mockReturnValue({
    mutateAsync: vi.fn(),
    isPending: false,
  });
  apiMocks.logout.mockResolvedValue("/signed-out");
  apiMocks.recordPageView.mockResolvedValue(undefined);
});

afterEach(() => {
  vi.clearAllMocks();
  vi.unstubAllGlobals();
});

describe("application composition", () => {
  it("renders loading, full-screen sign-in, identity-error, and no-access states", async () => {
    const user = userEvent.setup();
    const assign = vi.fn();
    vi.stubGlobal("location", { assign });
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
      screen.getByRole("heading", { name: "Sign in to Registry" }),
    ).toBeVisible();
    expect(
      screen.getByText(
        "Use your organization’s Microsoft account to continue.",
      ),
    ).toBeVisible();
    await user.click(
      screen.getByRole("button", { name: "Continue with Microsoft" }),
    );
    expect(assign).toHaveBeenCalledWith("/oauth2/authorization/entra");
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
    expect(apiMocks.recordPageView).toHaveBeenCalledWith("/", "csrf");
    expect(
      screen.queryByRole("combobox", { name: "Access context" }),
    ).toBeNull();
    await user.click(screen.getByRole("button", { name: /Ada Lovelace/i }));
    expect(screen.queryByText(/APM group/i)).toBeNull();
    await user.click(
      screen.getByRole("menuitem", { name: "Switch to dark mode" }),
    );
    expect(document.documentElement.dataset["theme"]).toBe("dark");
    await user.click(screen.getByRole("button", { name: "Browse" }));
    expect(screen.getByRole("menuitem", { name: /^Providers/ })).toBeVisible();
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

  it("provides administrators a complete settings and operations workspace", async () => {
    const user = userEvent.setup();
    const mutateAsync = vi.fn().mockResolvedValue(undefined);
    hookMocks.useSession.mockReturnValueOnce(
      sessionResult({ data: { ...session, admin: true } }),
    );
    hookMocks.useUpdateHomepageSettings.mockReturnValue({
      mutateAsync,
      isPending: false,
      isSuccess: false,
      error: null,
    });
    const createCredential = vi.fn().mockResolvedValue({
      credential: {
        id: "credential-1",
        name: "GitHub modules",
        scope: "module",
        keyPrefix: "rgs.credenti",
        createdBy: "user-1",
        createdAt: "2026-07-23T12:00:00Z",
        expiresAt: "2026-10-21T12:00:00Z",
        useCount: 0,
        status: "active",
      },
      token: "rgs.credential-1.one-time-secret",
    });
    hookMocks.useCreateSyncCredential.mockReturnValue({
      mutateAsync: createCredential,
      isPending: false,
      error: null,
    });
    const { container } = renderShell();

    await user.click(screen.getByRole("button", { name: /Ada Lovelace/i }));
    await user.click(screen.getByRole("menuitem", { name: "Admin settings" }));
    expect(
      screen.getByRole("heading", {
        name: "Registry settings and operations",
      }),
    ).toBeVisible();
    expect(screen.getByText("Registry systems are healthy")).toBeVisible();
    expect(screen.getByText("12,400")).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Traffic" }));
    expect(
      screen.getByRole("heading", { name: "Traffic analytics" }),
    ).toBeVisible();
    expect(screen.getByText("1,284")).toBeVisible();
    expect(screen.getAllByText("Ada Lovelace").length).toBeGreaterThan(0);
    expect(screen.getAllByText("/modules").length).toBeGreaterThan(0);

    await user.click(screen.getByRole("button", { name: "Homepage" }));
    const title = screen.getByRole("textbox", { name: "Title" });
    await user.clear(title);
    await user.type(title, "Planned maintenance");
    const message = screen.getByRole("textbox", { name: "Message" });
    await user.clear(message);
    await user.type(message, "The catalog will be read-only tonight.");
    await user.click(screen.getByRole("checkbox", { name: "Visible" }));
    await user.type(
      screen.getByRole("textbox", { name: "Link label (optional)" }),
      "Status",
    );
    await user.type(
      screen.getByRole("textbox", { name: "HTTPS or Registry-relative URL" }),
      "/status",
    );
    await user.type(
      screen.getByRole("searchbox", { name: "Search providers" }),
      "aws",
    );
    await user.click(screen.getByRole("button", { name: /hashicorp\/aws/i }));
    await user.click(
      screen.getByRole("checkbox", {
        name: "Featured providers visibility",
      }),
    );
    await user.click(
      screen.getByRole("checkbox", {
        name: "Featured modules visibility",
      }),
    );
    await user.click(
      screen.getByRole("button", {
        name: "Remove module/terraform-aws-modules/iam/aws",
      }),
    );
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        notificationEnabled: false,
        notificationTitle: "Planned maintenance",
        notificationMessage: "The catalog will be read-only tonight.",
        notificationLinkLabel: "Status",
        notificationLinkUrl: "/status",
        featuredProvidersEnabled: false,
        featuredModulesEnabled: false,
        featuredProviderIds: [],
        featuredModuleIds: ["module/terraform-aws-modules/iam/aws"],
      }),
    );

    expect(screen.getByRole("link", { name: "API reference" })).toHaveAttribute(
      "href",
      "/swagger-ui.html",
    );
    await user.click(screen.getByRole("button", { name: "Sync credentials" }));
    const createButtons = screen.getAllByRole("button", {
      name: "Create credential",
    });
    const createSubmit = createButtons.at(-1);
    if (createSubmit === undefined) throw new Error("Expected create submit");
    await user.click(createSubmit);
    await user.type(
      screen.getByRole("textbox", { name: "Name" }),
      "GitHub modules",
    );
    const submitButtons = screen.getAllByRole("button", {
      name: "Create credential",
    });
    const credentialSubmit = submitButtons.at(-1);
    if (credentialSubmit === undefined)
      throw new Error("Expected credential submit");
    await user.click(credentialSubmit);
    expect(createCredential).toHaveBeenCalledWith({
      name: "GitHub modules",
      scope: "module",
      expiresInDays: 90,
    });
    expect(screen.getByText("Credential created")).toBeVisible();
    expect(screen.getByText("rgs.credential-1.one-time-secret")).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Operational logs" }));
    expect(screen.getByText("No activity recorded")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Audit log" }));
    expect(screen.getByText("Administrator audit log")).toBeVisible();

    const result = await axe.run(container, {
      rules: { "color-contrast": { enabled: false } },
    });
    expect(result.violations).toEqual([]);
  });

  it("surfaces degraded operations, immutable audit activity, and credential revocation", async () => {
    const user = userEvent.setup();
    const revokeCredential = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    hookMocks.useSession.mockReturnValue(
      sessionResult({ data: { ...session, admin: true } }),
    );
    hookMocks.useAdminDashboard.mockReturnValue({
      data: {
        generatedAt: "2026-07-23T12:00:00Z",
        status: "degraded",
        workerEnabled: false,
        dependencies: {
          postgresql: "up",
          artifactory: "not_configured",
        },
        catalog: {
          providers: 0,
          modules: 0,
          activeVersions: 0,
          documents: 0,
          downloads: 0,
        },
        queue: {
          queued: 2,
          processing: 1,
          retry: 1,
          completed: 0,
          deadLetter: 1,
        },
        ingestion: {
          completed: 0,
          failed: 1,
          quarantined: 1,
          latencyP95Ms: 1400,
        },
        databaseSizeBytes: 512,
      },
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    });
    hookMocks.useAdminOperations.mockReturnValue({
      data: [
        {
          source: "queue",
          eventId: "event-1",
          status: "dead_letter",
          title: "Queue delivery",
          detail: "Attempts exhausted",
          repository: "iac-provider-release-local",
          path: "hashicorp/aws/1.0.0/linux_amd64.zip",
          correlationId: "correlation-1",
          occurredAt: "2026-07-23T12:00:00Z",
        },
      ],
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    });
    hookMocks.useAuditEvents.mockReturnValue({
      data: [
        {
          id: "audit-1",
          occurredAt: "2026-07-23T12:00:00Z",
          actorType: "user",
          actorId: "admin-1",
          action: "registry.homepage.updated",
          resourceType: "registry_homepage",
          resourceId: "home",
          correlationId: "correlation-1",
          detail: {},
        },
      ],
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    });
    hookMocks.useSyncCredentials.mockReturnValue({
      data: [
        {
          id: "credential-1",
          name: "Provider release",
          scope: "provider",
          keyPrefix: "rgs.credenti",
          createdBy: "admin-1",
          createdAt: "2026-07-23T12:00:00Z",
          expiresAt: "2026-10-21T12:00:00Z",
          lastUsedAt: "2026-07-23T12:30:00Z",
          useCount: 8,
          status: "active",
        },
        {
          id: "credential-2",
          name: "Old runner",
          scope: "all",
          keyPrefix: "rgs.oldrunne",
          createdBy: "admin-1",
          createdAt: "2026-01-01T12:00:00Z",
          expiresAt: "2026-02-01T12:00:00Z",
          useCount: 0,
          status: "expired",
        },
      ],
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    });
    hookMocks.useRevokeSyncCredential.mockReturnValue({
      mutateAsync: revokeCredential,
      isPending: false,
    });

    renderShell("/admin");
    expect(screen.getByText("Registry requires attention")).toBeVisible();
    expect(
      screen.getByText("No reconciliation run has been recorded."),
    ).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Operational logs" }));
    expect(screen.getByText("Attempts exhausted")).toBeVisible();
    expect(
      screen.getByText(
        "iac-provider-release-local/hashicorp/aws/1.0.0/linux_amd64.zip",
      ),
    ).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Audit log" }));
    expect(screen.getByText(/registry · homepage · updated/i)).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Sync credentials" }));
    expect(screen.getByText("Provider release")).toBeVisible();
    expect(screen.getByText("Never")).toBeVisible();
    await user.click(
      screen.getByRole("button", { name: "Revoke Provider release" }),
    );
    expect(revokeCredential).toHaveBeenCalledWith("credential-1");
  });

  it("renders truthful home counts and catalog content", () => {
    renderWithRegistry(<HomePage />);
    expect(hookMocks.useCatalogPage).toHaveBeenNthCalledWith(1, {
      kind: "provider",
      sort: "name",
      limit: 1,
    });
    expect(hookMocks.useCatalogPage).toHaveBeenNthCalledWith(2, {
      kind: "module",
      sort: "name",
      limit: 1,
    });
    expect(hookMocks.useFeaturedPackages).toHaveBeenNthCalledWith(1, [
      "provider/hashicorp/aws",
    ]);
    expect(hookMocks.useFeaturedPackages).toHaveBeenNthCalledWith(2, [
      "module/terraform-aws-modules/iam/aws",
    ]);
    expect(screen.getAllByText("1")).toHaveLength(2);
    expect(
      screen.getByRole("link", { name: /AWSby HashiCorp/i }),
    ).toBeVisible();
    expect(
      screen.getByRole("heading", {
        name: "How Terraform, providers and modules work",
      }),
    ).toBeVisible();
    expect(screen.queryByText(/APM group/i)).toBeNull();
  });

  it("does not replace an intentionally empty featured catalog", () => {
    hookMocks.useHomepageSettings.mockReturnValue({
      data: {
        notificationEnabled: true,
        notificationTitle: "Registry notice",
        notificationMessage: "Terraform catalog content.",
        featuredProvidersEnabled: true,
        featuredModulesEnabled: true,
        featuredProviderIds: [],
        featuredModuleIds: [],
        updatedAt: "2026-07-23T12:00:00Z",
      },
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderWithRegistry(<HomePage />);

    expect(screen.queryByRole("link", { name: /AWSby HashiCorp/i })).toBeNull();
    expect(
      screen.queryByRole("link", { name: /terraform-aws-modules/i }),
    ).toBeNull();
    expect(
      screen.getAllByRole("heading", {
        name: "No packages match these filters",
      }),
    ).toHaveLength(2);
  });

  it("independently hides disabled featured homepage sections", () => {
    hookMocks.useHomepageSettings.mockReturnValue({
      data: {
        notificationEnabled: true,
        notificationTitle: "Registry notice",
        notificationMessage: "Terraform catalog content.",
        featuredProvidersEnabled: false,
        featuredModulesEnabled: true,
        featuredProviderIds: ["provider/hashicorp/aws"],
        featuredModuleIds: ["module/terraform-aws-modules/iam/aws"],
        updatedAt: "2026-07-23T12:00:00Z",
      },
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderWithRegistry(<HomePage />);

    expect(hookMocks.useFeaturedPackages).toHaveBeenNthCalledWith(1, []);
    expect(hookMocks.useFeaturedPackages).toHaveBeenNthCalledWith(2, [
      "module/terraform-aws-modules/iam/aws",
    ]);
    expect(
      screen.queryByText("Featured providers", { selector: "p" }),
    ).toBeNull();
    expect(
      screen.getByText("Featured modules", { selector: "p" }),
    ).toBeVisible();
    expect(
      screen.getByRole("heading", {
        name: "How Terraform, providers and modules work",
      }),
    ).toBeVisible();
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
    hookMocks.useFeaturedPackages.mockReturnValue({
      items: [],
      isPending: false,
      isError: false,
    });
    const adminSession = { ...session, admin: true, apms: [] };
    renderWithRegistry(<HomePage />, "/", adminSession);
    expect(screen.queryByText(/administrator|APM group/i)).toBeNull();
    expect(
      screen.getAllByRole("heading", {
        name: "No packages match these filters",
      }),
    ).toHaveLength(2);
  });

  it("updates module filters and pagination without changing scroll position", async () => {
    const user = userEvent.setup();
    const scrollTo = vi.spyOn(window, "scrollTo");
    hookMocks.useCatalogPage.mockReturnValue(
      queryResult({
        items: [provider, modulePackage],
        total: 42,
        nextCursor: "page-2",
      }),
    );
    renderWithRegistry(<CatalogPage kind="module" />, "/modules?provider=aws");

    expect(screen.getByText("1–2 of 42")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Filter Modules" }));
    expect(screen.getByRole("checkbox", { name: "AWS" })).toBeChecked();
    await user.click(screen.getByRole("checkbox", { name: "Azure" }));
    expect(scrollTo).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Next page" }));
    expect(screen.getByText("10–11 of 42")).toBeVisible();
    expect(scrollTo).not.toHaveBeenCalled();
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

  it("redirects the removed documentation route to the homepage", () => {
    renderShell("/docs");
    expect(screen.getByText("Authenticated outlet")).toBeVisible();
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
