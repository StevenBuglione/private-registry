import { afterEach, describe, expect, it, vi } from "vitest";
import {
  getPackage,
  getPackageDocumentation,
  getPackageGovernance,
  getSession,
  logout,
  normalizeCatalogPage,
} from "./api";

afterEach(() => vi.unstubAllGlobals());

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("OpenAPI response normalization", () => {
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
          owners: ["Cloud Platform"],
          support_level: "supported",
          lifecycle: "approved",
          verification: "enterprise-verified",
          risk_tier: "medium",
          source_address: "registry.example/hashicorp/aws",
          updated_at: "2026-07-22T12:00:00Z",
          versions: [{ version: "6.8.0" }, { version: "6.7.0" }],
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
      owner: "Cloud Platform",
      version: "6.8.0",
      lifecycle: "approved",
      risk: "medium",
      verified: true,
    });
  });

  it("normalizes snake_case package and governance source fields", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({
          kind: "provider",
          namespace: "hashicorp",
          name: "aws",
          target: "",
          description: "AWS infrastructure provider",
          latest_version: "6.8.0",
          owners: ["Cloud Platform"],
          lifecycle: "approved",
          verification: "enterprise-verified",
          risk_tier: "medium",
          source_address: "registry.example/hashicorp/aws",
          updated_at: "2026-07-22T12:00:00Z",
          versions: [
            {
              version: "6.8.0",
              artifact_repository: "iac-provider-release-local",
              artifact_path:
                "hashicorp/aws/6.8.0/terraform-provider-aws_6.8.0_linux_amd64.zip",
              package_digest: `sha256:${"a".repeat(64)}`,
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
      )
      .mockResolvedValueOnce(
        jsonResponse({
          package_id: "provider/hashicorp/aws",
          owners: ["Cloud Platform"],
          support_level: "supported",
          lifecycle: "approved",
          risk_tier: "medium",
          verification: "enterprise-verified",
          approvals: [],
          source_address: "registry.example/hashicorp/aws",
          version_constraint: ">= 6.0",
          support_url: null,
          source_repository_url: "https://example.test/source",
          jfrog_console_url: "https://example.test/artifactory",
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
    await expect(
      getPackageGovernance("provider", "hashicorp", "aws"),
    ).resolves.toMatchObject({
      owner: "Cloud Platform",
      support: "supported",
      lifecycle: "approved",
      risk: "medium",
      sourceRepository: "https://example.test/source",
      artifactRepository: "registry.example/hashicorp/aws",
    });
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
        "APM0000001",
        "resources/aws_vpc.md",
      ),
    ).resolves.toBe("# aws_vpc Resource");
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(
        /documentation\?apm_id=APM0000001&path=resources%2Faws_vpc\.md$/,
      ),
      expect.any(Object),
    );
  });
});
