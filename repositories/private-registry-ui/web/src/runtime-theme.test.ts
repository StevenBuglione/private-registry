import { afterEach, describe, expect, it, vi } from "vitest";
import { loadRuntimeConfig, runtimeConfig } from "./runtime-config";
import { currentTheme, initializeTheme, saveTheme } from "./theme";

afterEach(() => {
  localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
  vi.unstubAllGlobals();
});

describe("runtime configuration", () => {
  it("uses safe defaults for malformed optional values and sends a no-cache request", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          apiBaseUrl: 42,
          jfrogHostname: null,
          environment: false,
          supportUrl: [],
        }),
        { status: 200 },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(loadRuntimeConfig()).resolves.toEqual({
      apiBaseUrl: "/api/v1",
      jfrogHostname: "",
      environment: "unknown",
      supportUrl: "",
    });
    expect(fetchMock).toHaveBeenCalledWith("/config/runtime.json", {
      cache: "no-store",
      credentials: "same-origin",
    });
  });

  it("validates and normalizes a generated runtime payload", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            apiBaseUrl: "/internal/api/",
            jfrogHostname: "artifactory.example.test",
            environment: "acceptance",
            supportUrl: "https://support.example.test",
          }),
          { status: 200 },
        ),
      ),
    );

    await expect(loadRuntimeConfig()).resolves.toEqual({
      apiBaseUrl: "/internal/api",
      jfrogHostname: "artifactory.example.test",
      environment: "acceptance",
      supportUrl: "https://support.example.test",
    });
    expect(runtimeConfig().environment).toBe("acceptance");
  });

  it("keeps the last valid configuration on invalid payloads and outages", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(null, { status: 503 })),
    );
    await expect(loadRuntimeConfig()).resolves.toEqual(runtimeConfig());

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("[]")));
    await expect(loadRuntimeConfig()).resolves.toEqual(runtimeConfig());

    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    await expect(loadRuntimeConfig()).resolves.toEqual(runtimeConfig());
  });
});

describe("theme persistence", () => {
  it("uses system preference, persists overrides, and updates browser chrome", () => {
    const themeMeta = document.createElement("meta");
    themeMeta.name = "theme-color";
    document.head.append(themeMeta);
    Object.defineProperty(window, "matchMedia", {
      configurable: true,
      value: () => ({ matches: true }),
    });

    expect(initializeTheme()).toBe("dark");
    expect(currentTheme()).toBe("dark");
    expect(themeMeta.content).toBe("#0d0d0f");

    saveTheme("light");
    expect(localStorage.getItem("registry-theme")).toBe("light");
    expect(document.documentElement.dataset["theme"]).toBe("light");
    expect(document.documentElement.style.colorScheme).toBe("light");
    themeMeta.remove();
  });

  it("honors each stored theme and reports the applied theme", () => {
    Object.defineProperty(window, "matchMedia", {
      configurable: true,
      value: vi.fn().mockReturnValue({ matches: false }),
    });

    expect(initializeTheme()).toBe("light");
    expect(currentTheme()).toBe("light");

    localStorage.setItem("registry-theme", "dark");
    document.documentElement.removeAttribute("data-theme");
    expect(initializeTheme()).toBe("dark");
    expect(currentTheme()).toBe("dark");

    localStorage.setItem("registry-theme", "light");
    document.documentElement.dataset["theme"] = "light";
    expect(currentTheme()).toBe("light");
  });

  it("falls back to the light system preference for an invalid stored value", () => {
    const matchMedia = vi.fn().mockReturnValue({ matches: false });
    Object.defineProperty(window, "matchMedia", {
      configurable: true,
      value: matchMedia,
    });
    localStorage.setItem("registry-theme", "sepia");

    expect(initializeTheme()).toBe("light");
    expect(matchMedia).toHaveBeenCalledWith("(prefers-color-scheme: dark)");
    expect(document.documentElement.style.colorScheme).toBe("light");
  });
});
