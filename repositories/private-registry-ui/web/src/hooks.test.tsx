import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api/client";
import {
  useAdminDashboard,
  useAdminOperations,
  useAuditEvents,
  useCatalogEvents,
  useCatalogPage,
  useCatalogSuggestions,
  useCreateSyncCredential,
  useFeaturedPackages,
  usePackage,
  usePackageDocumentation,
  useSession,
  useSyncCredentials,
  useTrafficReport,
} from "./hooks";

const apiMocks = vi.hoisted(() => ({
  getSession: vi.fn(),
  getCatalogPage: vi.fn(),
  getPackage: vi.fn(),
  getPackageDocumentation: vi.fn(),
  getAdminDashboard: vi.fn(),
  getTrafficReport: vi.fn(),
  getAdminOperations: vi.fn(),
  getAuditEvents: vi.fn(),
  getSyncCredentials: vi.fn(),
  createSyncCredential: vi.fn(),
}));

vi.mock("./api/auth", () => ({
  getSession: apiMocks.getSession,
}));

vi.mock("./api/catalog", () => ({
  getCatalogPage: apiMocks.getCatalogPage,
  getPackage: apiMocks.getPackage,
  getPackageDocumentation: apiMocks.getPackageDocumentation,
}));

vi.mock("./api/admin", () => ({
  getAdminDashboard: apiMocks.getAdminDashboard,
  getTrafficReport: apiMocks.getTrafficReport,
  getAdminOperations: apiMocks.getAdminOperations,
  getAuditEvents: apiMocks.getAuditEvents,
  getSyncCredentials: apiMocks.getSyncCredentials,
  createSyncCredential: apiMocks.createSyncCredential,
}));

let queryClient: QueryClient;

function Wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

const identity = {
  kind: "provider" as const,
  namespace: "hashicorp",
  name: "aws",
  target: undefined,
  version: "6.8.0",
};

beforeEach(() => {
  queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  apiMocks.getSession.mockResolvedValue({ subject: "user-1" });
  apiMocks.getCatalogPage.mockResolvedValue({ items: [], total: 0 });
  apiMocks.getPackage.mockResolvedValue({ name: "aws" });
  apiMocks.getPackageDocumentation.mockResolvedValue("# AWS");
  apiMocks.getAdminDashboard.mockResolvedValue({ status: "healthy" });
  apiMocks.getTrafficReport.mockResolvedValue({ days: 30 });
  apiMocks.getAdminOperations.mockResolvedValue([]);
  apiMocks.getAuditEvents.mockResolvedValue([]);
  apiMocks.getSyncCredentials.mockResolvedValue([]);
  apiMocks.createSyncCredential.mockResolvedValue({
    credential: { id: "credential-1" },
    token: "one-time-secret",
  });
});

afterEach(() => {
  queryClient.clear();
  vi.clearAllMocks();
  vi.unstubAllGlobals();
});

describe("query hooks", () => {
  it("loads sessions, catalog pages, and enabled suggestions", async () => {
    const sessionHook = renderHook(() => useSession(), { wrapper: Wrapper });
    await waitFor(() => {
      expect(sessionHook.result.current.isSuccess).toBe(true);
    });
    expect(apiMocks.getSession).toHaveBeenCalledOnce();

    const catalogHook = renderHook(
      () => useCatalogPage({ kind: "provider", limit: 20 }),
      { wrapper: Wrapper },
    );
    await waitFor(() => {
      expect(catalogHook.result.current.isSuccess).toBe(true);
    });
    expect(apiMocks.getCatalogPage).toHaveBeenCalledWith({
      kind: "provider",
      limit: 20,
    });

    const suggestionHook = renderHook(() => useCatalogSuggestions("aws"), {
      wrapper: Wrapper,
    });
    await waitFor(() => {
      expect(suggestionHook.result.current.isSuccess).toBe(true);
    });
    expect(apiMocks.getCatalogPage).toHaveBeenCalledWith({
      q: "aws",
      sort: "relevance",
      limit: 8,
    });
  });

  it("does not query incomplete suggestions", () => {
    renderHook(() => useCatalogSuggestions("a"), { wrapper: Wrapper });
    expect(apiMocks.getCatalogPage).not.toHaveBeenCalled();
  });

  it("loads configured featured packages by exact authorized identity", async () => {
    apiMocks.getPackage.mockImplementation(
      (
        kind: "provider" | "module",
        namespace: string,
        name: string,
        target?: string,
      ) => {
        if (name === "missing") {
          throw new ApiError("Not found", 404, "not_found");
        }
        return {
          kind,
          registryTier: kind === "provider" ? "official" : "community",
          namespace,
          name,
          target,
          provider: target ?? name,
          version: "1.0.0",
          description: `${name} package`,
          verified: true,
          updatedAt: "2026-07-23T12:00:00Z",
        };
      },
    );

    const featured = renderHook(
      () =>
        useFeaturedPackages([
          "provider/platform/provider-075",
          "module/platform/module-150/aws",
          "provider/platform/missing",
          "not/a/package/id",
          "provider/platform/provider-075",
        ]),
      { wrapper: Wrapper },
    );

    await waitFor(() => {
      expect(featured.result.current.isPending).toBe(false);
    });
    expect(
      queryClient
        .getQueryCache()
        .getAll()
        .flatMap((query) =>
          query.state.error === null ? [] : [query.state.error],
        ),
    ).toEqual([expect.objectContaining({ status: 404 })]);
    expect(featured.result.current.isError).toBe(false);
    expect(featured.result.current.items.map((item) => item.name)).toEqual([
      "provider-075",
      "module-150",
    ]);
    expect(apiMocks.getPackage.mock.calls).toEqual([
      ["provider", "platform", "provider-075", undefined],
      ["module", "platform", "module-150", "aws"],
      ["provider", "platform", "missing", undefined],
    ]);
    expect(apiMocks.getCatalogPage).not.toHaveBeenCalled();
  });

  it("surfaces non-404 failures from exact featured package lookups", async () => {
    apiMocks.getPackage.mockRejectedValue(
      new ApiError("Catalog unavailable", 503, "catalog_unavailable"),
    );
    const featured = renderHook(
      () => useFeaturedPackages(["provider/platform/aws"]),
      { wrapper: Wrapper },
    );

    await waitFor(
      () => {
        expect(featured.result.current.isPending).toBe(false);
      },
      { timeout: 3_000 },
    );
    expect(featured.result.current.isError).toBe(true);
    expect(featured.result.current.items).toEqual([]);
  });

  it("loads package details and documentation", async () => {
    const packageHook = renderHook(() => usePackage(identity), {
      wrapper: Wrapper,
    });
    await waitFor(() => {
      expect(packageHook.result.current.isSuccess).toBe(true);
    });
    expect(apiMocks.getPackage).toHaveBeenCalledWith(
      "provider",
      "hashicorp",
      "aws",
      undefined,
      "6.8.0",
    );

    const docsHook = renderHook(
      () => usePackageDocumentation(identity, undefined, "resources/aws.md"),
      { wrapper: Wrapper },
    );
    await waitFor(() => {
      expect(docsHook.result.current.isSuccess).toBe(true);
    });
    expect(apiMocks.getPackageDocumentation).toHaveBeenCalledWith(
      "provider",
      "hashicorp",
      "aws",
      undefined,
      "6.8.0",
      "resources/aws.md",
    );
  });

  it("loads administrator telemetry and invalidates credential queries after creation", async () => {
    const dashboard = renderHook(() => useAdminDashboard(), {
      wrapper: Wrapper,
    });
    const traffic = renderHook(() => useTrafficReport(30), {
      wrapper: Wrapper,
    });
    const operations = renderHook(() => useAdminOperations(), {
      wrapper: Wrapper,
    });
    const audit = renderHook(() => useAuditEvents(), { wrapper: Wrapper });
    const credentials = renderHook(() => useSyncCredentials(), {
      wrapper: Wrapper,
    });
    const create = renderHook(() => useCreateSyncCredential("csrf"), {
      wrapper: Wrapper,
    });

    await waitFor(() => {
      expect(dashboard.result.current.isSuccess).toBe(true);
      expect(traffic.result.current.isSuccess).toBe(true);
      expect(operations.result.current.isSuccess).toBe(true);
      expect(audit.result.current.isSuccess).toBe(true);
      expect(credentials.result.current.isSuccess).toBe(true);
    });
    await create.result.current.mutateAsync({
      name: "GitHub modules",
      scope: "module",
      expiresInDays: 90,
    });

    expect(apiMocks.getAdminDashboard).toHaveBeenCalledOnce();
    expect(apiMocks.getTrafficReport).toHaveBeenCalledWith(30);
    expect(apiMocks.getAdminOperations).toHaveBeenCalledOnce();
    expect(apiMocks.getAuditEvents).toHaveBeenCalled();
    expect(apiMocks.createSyncCredential).toHaveBeenCalledWith(
      {
        name: "GitHub modules",
        scope: "module",
        expiresInDays: 90,
      },
      "csrf",
    );
  });
});

describe("catalog event stream", () => {
  it("invalidates authorized query families and closes cleanly", () => {
    class FakeEventSource {
      static current: FakeEventSource | undefined;
      readonly close = vi.fn();
      readonly listeners = new Map<string, () => void>();
      onmessage: (() => void) | null = null;

      constructor(
        readonly url: string,
        readonly options: EventSourceInit,
      ) {
        FakeEventSource.current = this;
      }

      addEventListener(name: string, listener: () => void): void {
        this.listeners.set(name, listener);
      }
    }

    vi.stubGlobal("EventSource", FakeEventSource);
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    const stream = renderHook(
      () => {
        useCatalogEvents();
      },
      {
        wrapper: Wrapper,
      },
    );
    const currentSource = FakeEventSource.current;
    if (currentSource === undefined)
      throw new Error("Expected an event stream");

    expect(currentSource.url).toBe("/api/v1/catalog/events");
    expect(currentSource.options).toEqual({ withCredentials: true });
    currentSource.listeners.get("catalog-change")?.();
    currentSource.onmessage?.();
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["catalog"] });

    stream.unmount();
    expect(currentSource.close).toHaveBeenCalledOnce();
  });
});
