import { render, renderHook, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PackageIcon } from "./components/PackageIcon";
import { RegistryBrand, RegistryMark } from "./components/RegistryMark";
import { useRegistry } from "./use-registry";
import { formatRelativeDate, hasText, packageHref } from "./utils";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("shared UI contracts", () => {
  it("fails fast when registry access is used outside its provider", () => {
    expect(() => renderHook(() => useRegistry())).toThrow(
      "useRegistry must be used inside RegistryProvider",
    );
  });

  it("renders the optimized container marks and the Oremus Labs lockup", () => {
    const { container } = render(
      <>
        <RegistryMark />
        <RegistryMark compact />
        <RegistryBrand />
      </>,
    );
    expect(
      container.querySelectorAll(
        '.brand-mark-frame img[src="/assets/registry-mark.svg"]',
      ),
    ).toHaveLength(3);
    expect(container.querySelector(".brand-mark-frame.compact")).not.toBeNull();
    expect(screen.getByText("Oremus Labs")).toBeInTheDocument();
    expect(screen.getByText("Terraform")).toBeInTheDocument();
    expect(screen.getByText("Registry")).toBeInTheDocument();
  });

  it("falls back to a neutral package glyph and formats fresh timestamps", () => {
    render(<PackageIcon kind="provider" name="internal" />);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(formatRelativeDate(new Date().toISOString())).toBe("Just now");
  });

  it("builds encoded canonical package links", () => {
    expect(
      packageHref({
        kind: "provider",
        namespace: "internal tools",
        name: "azure/ad",
        version: "1.2.3+build",
      }),
    ).toBe("/providers/internal%20tools/azure%2Fad/1.2.3%2Bbuild");
    expect(
      packageHref({
        kind: "module",
        namespace: "platform",
        name: "network",
        target: undefined,
        version: "2.0.0",
      }),
    ).toBe("/modules/platform/network/general/2.0.0");
    expect(
      packageHref({
        kind: "module",
        namespace: "platform",
        name: "network",
        target: "azure/us gov",
        version: "2.0.0",
      }),
    ).toBe("/modules/platform/network/azure%2Fus%20gov/2.0.0");
  });

  it("formats empty, invalid, and boundary timestamps deliberately", () => {
    vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-07-22T12:00:00Z"));

    expect(formatRelativeDate("")).toBe("Recently");
    expect(formatRelativeDate("not-a-date")).toBe("not-a-date");
    expect(formatRelativeDate("2025-07-22T12:00:00Z")).toBe("last year");
    expect(formatRelativeDate("2026-06-22T12:00:00Z")).toBe("last month");
    expect(formatRelativeDate("2026-07-15T12:00:00Z")).toBe("last week");
    expect(formatRelativeDate("2026-07-21T12:00:00Z")).toBe("yesterday");
    expect(formatRelativeDate("2026-07-22T10:00:00Z")).toBe("2 hours ago");
    expect(formatRelativeDate("2026-07-22T11:58:00Z")).toBe("2 minutes ago");
    expect(formatRelativeDate("2026-07-22T11:59:01Z")).toBe("Just now");
  });

  it("accepts only non-empty strings as text", () => {
    expect(hasText("registry")).toBe(true);
    expect(hasText(" ")).toBe(true);
    expect(hasText("")).toBe(false);
    expect(hasText(null)).toBe(false);
    expect(hasText(undefined)).toBe(false);
  });
});
