import { afterEach, describe, expect, it, vi } from "vitest";
import {
  createSyncCredential,
  getAdminDashboard,
  getAdminOperations,
  getAuditEvents,
  getCatalogPage,
  getHomepageSettings,
  getPackage,
  getPackageDocumentation,
  getSession,
  getSyncCredentials,
  getTrafficReport,
  logout,
  normalizeCatalogPage,
  recordPageView,
  revokeSyncCredential,
  updateHomepageSettings,
} from "./api";

afterEach(() => vi.unstubAllGlobals());

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("OpenAPI response normalization", () => {
  it("sends multi-value browse filters to the catalog API", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse({ items: [], total: 0 }));
    vi.stubGlobal("fetch", fetchMock);

    await getCatalogPage({
      kind: "provider",
      namespace: "hashicorp",
      tier: "official,partner",
      category: "public-cloud,networking",
      provider: "aws,azure",
      page: 2,
      limit: 50,
    });

    const call = fetchMock.mock.calls[0];
    if (call === undefined) throw new Error("Expected a catalog request");
    const input = call[0];
    const requestUrl =
      typeof input === "string"
        ? input
        : input instanceof URL
          ? input.toString()
          : input.url;
    const url = new URL(requestUrl, "http://registry.test");
    expect(url.searchParams.get("tier")).toBe("official,partner");
    expect(url.searchParams.get("namespace")).toBe("hashicorp");
    expect(url.searchParams.get("category")).toBe("public-cloud,networking");
    expect(url.searchParams.get("provider")).toBe("aws,azure");
    expect(url.searchParams.get("page")).toBe("2");
    expect(url.searchParams.get("limit")).toBe("50");
  });

  it("preserves snake_case APM entitlements from the session", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse({
          subject: "entra-user",
          display_name: "APM user",
          email: "apm@example.test",
          roles: ["registry-user"],
          apm_entitlements: [
            { apm_id: "APM0000001", display_name: "Payments" },
          ],
          csrf_token: "csrf-value",
        }),
      ),
    );

    await expect(getSession()).resolves.toMatchObject({
      subject: "entra-user",
      displayName: "APM user",
      apms: [{ id: "APM0000001", name: "Payments" }],
      csrfToken: "csrf-value",
    });
  });

  it("uses the snake_case Entra logout redirect", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse({ redirect_uri: "/signed-out" }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(logout("csrf-value")).resolves.toBe("/signed-out");
    expect(fetchMock).toHaveBeenCalledOnce();
    const call = fetchMock.mock.calls[0];
    if (call === undefined) throw new Error("Expected a logout request");
    const requestUrl =
      typeof call[0] === "string"
        ? call[0]
        : call[0] instanceof URL
          ? call[0].toString()
          : call[0].url;
    expect(requestUrl).toContain("/auth/logout");
    expect(new Headers(call[1]?.headers).get("X-XSRF-TOKEN")).toBe(
      "csrf-value",
    );
  });

  it("loads and updates administrator-managed homepage settings", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(() =>
      Promise.resolve(
        jsonResponse({
          notification_enabled: true,
          notification_title: "Registry notice",
          notification_message: "Terraform packages are available.",
          featured_provider_ids: ["provider/hashicorp/aws"],
          featured_module_ids: ["module/terraform-aws-modules/iam/aws"],
          updated_at: "2026-07-22T12:00:00Z",
        }),
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    const settings = await getHomepageSettings();
    expect(settings).toMatchObject({
      notificationEnabled: true,
      notificationTitle: "Registry notice",
      featuredProviderIds: ["provider/hashicorp/aws"],
      featuredModuleIds: ["module/terraform-aws-modules/iam/aws"],
    });

    await updateHomepageSettings(
      {
        notificationEnabled: false,
        notificationTitle: "Maintenance",
        notificationMessage: "The catalog is read-only.",
        featuredProviderIds: [],
        featuredModuleIds: [],
      },
      "csrf-value",
    );
    const call = fetchMock.mock.calls[1];
    if (call === undefined) throw new Error("Expected a homepage update");
    expect(call[0]).toContain("/registry/homepage");
    expect(call[1]?.method).toBe("PUT");
    expect(new Headers(call[1]?.headers).get("X-XSRF-TOKEN")).toBe(
      "csrf-value",
    );
    const requestBody = call[1]?.body;
    if (typeof requestBody !== "string") {
      throw new Error("Expected a serialized homepage update body");
    }
    expect(JSON.parse(requestBody)).toEqual({
      notification_enabled: false,
      notification_title: "Maintenance",
      notification_message: "The catalog is read-only.",
      featured_provider_ids: [],
      featured_module_ids: [],
    });
  });

  it("normalizes administrator telemetry, audit history, and credential lifecycle requests", async () => {
    const credential = {
      id: "credential-1",
      name: "GitHub modules",
      scope: "MODULE",
      key_prefix: "rgs.credenti",
      created_by: "admin-1",
      created_at: "2026-07-23T12:00:00Z",
      expires_at: "2026-10-21T12:00:00Z",
      use_count: 3,
      status: "ACTIVE",
    };
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        jsonResponse({
          generated_at: "2026-07-23T12:00:00Z",
          status: "degraded",
          worker_enabled: true,
          dependencies: { postgresql: "up", artifactory: "down" },
          catalog: {
            providers: 12,
            modules: 30,
            active_versions: 84,
            documents: 240,
            downloads: 5000,
          },
          queue: {
            queued: 2,
            processing: 1,
            retry: 1,
            completed: 80,
            dead_letter: 1,
          },
          ingestion: {
            completed: 40,
            failed: 1,
            quarantined: 2,
            latency_p95_ms: 900,
          },
          database_size_bytes: 1048576,
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse([
          {
            source: "queue",
            event_id: "event-1",
            status: "dead_letter",
            title: "Queue delivery",
            detail: "Attempts exhausted",
            correlation_id: "correlation-1",
            occurred_at: "2026-07-23T12:00:00Z",
          },
        ]),
      )
      .mockResolvedValueOnce(
        jsonResponse([
          {
            id: "audit-1",
            occurred_at: "2026-07-23T12:00:00Z",
            actor_type: "user",
            actor_id: "admin-1",
            action: "registry.homepage.updated",
            resource_type: "registry_homepage",
            resource_id: "home",
            correlation_id: "correlation-1",
            detail: { after: { notification_enabled: true } },
          },
        ]),
      )
      .mockResolvedValueOnce(jsonResponse([credential]))
      .mockResolvedValueOnce(
        jsonResponse({ credential, token: "rgs.credential-1.secret" }),
      )
      .mockResolvedValueOnce(
        jsonResponse({ ...credential, status: "REVOKED" }),
      );
    vi.stubGlobal("fetch", fetchMock);

    await expect(getAdminDashboard()).resolves.toMatchObject({
      status: "degraded",
      dependencies: { artifactory: "down" },
      catalog: { providers: 12, activeVersions: 84 },
      queue: { deadLetter: 1 },
      ingestion: { latencyP95Ms: 900 },
    });
    await expect(getAdminOperations()).resolves.toMatchObject([
      { source: "queue", eventId: "event-1", status: "dead_letter" },
    ]);
    await expect(getAuditEvents()).resolves.toMatchObject([
      { actorId: "admin-1", resourceType: "registry_homepage" },
    ]);
    await expect(getSyncCredentials()).resolves.toMatchObject([
      { scope: "module", status: "active", useCount: 3 },
    ]);
    await expect(
      createSyncCredential(
        { name: "GitHub modules", scope: "module", expiresInDays: 90 },
        "csrf-value",
      ),
    ).resolves.toMatchObject({ token: "rgs.credential-1.secret" });
    await expect(
      revokeSyncCredential("credential-1", "csrf-value"),
    ).resolves.toMatchObject({ status: "revoked" });

    const createCall = fetchMock.mock.calls[4];
    if (createCall === undefined) throw new Error("Expected credential create");
    expect(createCall[1]?.method).toBe("POST");
    expect(createCall[1]?.body).toBe(
      JSON.stringify({
        name: "GitHub modules",
        scope: "module",
        expires_in_days: 90,
      }),
    );
    const revokeCall = fetchMock.mock.calls[5];
    if (revokeCall === undefined) throw new Error("Expected credential revoke");
    expect(revokeCall[1]?.method).toBe("DELETE");
  });

  it("records authenticated navigation and normalizes administrator traffic analytics", async () => {
    document.cookie = "XSRF-TOKEN=csrf-cookie";
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(
        jsonResponse({
          generated_at: "2026-07-23T12:00:00Z",
          days: 30,
          summary: {
            page_views: 12,
            unique_visitors: 3,
            page_views_today: 5,
            visitors_today: 2,
          },
          daily: [
            {
              day: "2026-07-23",
              page_views: 5,
              unique_visitors: 2,
            },
          ],
          top_routes: [
            {
              path: "/modules",
              page_views: 8,
              unique_visitors: 3,
              last_viewed_at: "2026-07-23T11:59:00Z",
            },
          ],
          visitors: [
            {
              subject: "user-1",
              display_name: "Ada Lovelace",
              email: "ada@example.test",
              page_views: 8,
              first_seen_at: "2026-07-20T10:00:00Z",
              last_seen_at: "2026-07-23T11:59:00Z",
              last_path: "/modules",
            },
          ],
          recent_access: [
            {
              subject: "user-1",
              display_name: "Ada Lovelace",
              path: "/modules",
              occurred_at: "2026-07-23T11:59:00Z",
            },
          ],
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      recordPageView("/modules", "csrf-value"),
    ).resolves.toBeUndefined();
    await expect(getTrafficReport(30)).resolves.toMatchObject({
      days: 30,
      summary: { pageViews: 12, uniqueVisitors: 3 },
      topRoutes: [{ path: "/modules", pageViews: 8 }],
      visitors: [{ displayName: "Ada Lovelace", lastPath: "/modules" }],
      recentAccess: [{ path: "/modules" }],
    });

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      expect.stringContaining("/analytics/page-views"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ path: "/modules" }),
      }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      expect.stringContaining("/admin/traffic?days=30&visitorLimit=50"),
      expect.any(Object),
    );
  });

  it("surfaces the nested OpenAPI error code and message", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            error: {
              code: "identity_unavailable",
              message: "Graph unavailable",
            },
          },
          503,
        ),
      ),
    );

    await expect(getSession()).rejects.toMatchObject({
      status: 503,
      code: "identity_unavailable",
      message: "Graph unavailable",
    });
  });

  it("normalizes an exact snake_case CatalogPackage", () => {
    const page = normalizeCatalogPage({
      items: [
        {
          id: "provider/hashicorp/aws",
          kind: "provider",
          namespace: "hashicorp",
          name: "aws",
          target: "",
          title: "AWS",
          description: "AWS infrastructure provider",
          latest_version: "6.8.0",
          verification: "enterprise-verified",
          source_address: "registry.example/hashicorp/aws",
          updated_at: "2026-07-22T12:00:00Z",
          versions: [
            {
              version: "6.8.0",
              download_statistics: {
                download_count: 11,
                observed_at: "2026-07-22T12:00:00Z",
              },
            },
            {
              version: "6.7.0",
              download_statistics: {
                download_count: 7,
                observed_at: "2026-07-22T11:00:00Z",
              },
            },
          ],
          symbols: [
            {
              kind: "input",
              name: "region",
              description: "Deployment region.",
              path: "#inputs",
              type: "string",
              default_value: "us-east-1",
              required: false,
              sensitive: false,
            },
          ],
        },
      ],
      next_cursor: "next-page",
      total: 12,
    });

    expect(page).toMatchObject({ total: 12, nextCursor: "next-page" });
    expect(page.items[0]).toMatchObject({
      kind: "provider",
      provider: "aws",
      version: "6.8.0",
      verified: true,
      downloadStatistics: {
        allTime: 18,
        observedAt: "2026-07-22T12:00:00Z",
      },
    });
  });

  it("normalizes snake_case package source fields", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(
      jsonResponse({
        kind: "provider",
        namespace: "hashicorp",
        name: "aws",
        target: "",
        description: "AWS infrastructure provider",
        latest_version: "6.8.0",
        verification: "enterprise-verified",
        source_address: "registry.example/hashicorp/aws",
        updated_at: "2026-07-22T12:00:00Z",
        versions: [
          {
            version: "6.8.0",
            artifact_repository: "iac-provider-release-local",
            artifact_path:
              "hashicorp/aws/6.8.0/terraform-provider-aws_6.8.0_linux_amd64.zip",
            package_digest: `sha256:${"a".repeat(64)}`,
            download_statistics: {
              download_count: 11,
              week_downloads: 4,
              observed_at: "2026-07-22T12:00:00Z",
            },
          },
          {
            version: "6.7.0",
            download_statistics: {
              download_count: 7,
              week_downloads: 3,
              observed_at: "2026-07-22T11:00:00Z",
            },
          },
        ],
        symbols: [
          {
            kind: "input",
            name: "region",
            description: "Deployment region.",
            path: "#inputs",
            type: "string",
            default_value: "us-east-1",
            required: false,
            sensitive: false,
          },
        ],
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      getPackage("provider", "hashicorp", "aws"),
    ).resolves.toMatchObject({
      installSource: "registry.example/hashicorp/aws",
      verified: true,
      artifactRepository: "iac-provider-release-local",
      artifactPath:
        "hashicorp/aws/6.8.0/terraform-provider-aws_6.8.0_linux_amd64.zip",
      downloadStatistics: {
        allTime: 18,
        week: 7,
        observedAt: "2026-07-22T12:00:00Z",
      },
      symbols: [
        {
          kind: "input",
          name: "region",
          path: "#inputs",
          type: "string",
          defaultValue: "us-east-1",
          required: false,
          sensitive: false,
        },
      ],
    });
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("requests an authorized selected documentation path", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response("# aws_vpc Resource", {
        status: 200,
        headers: { "content-type": "text/markdown" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      getPackageDocumentation(
        "provider",
        "hashicorp",
        "aws",
        undefined,
        "6.8.0",
        "resources/aws_vpc.md",
      ),
    ).resolves.toBe("# aws_vpc Resource");
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/documentation\?path=resources%2Faws_vpc\.md$/),
      expect.any(Object),
    );
  });
});
