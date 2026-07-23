import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import axe from "axe-core";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { type FilterState, Filters } from "./Filters";

function FilterHarness({
  kind = "provider",
}: {
  kind?: "provider" | "module";
}) {
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState<FilterState>({});
  return (
    <>
      <button
        type="button"
        onClick={() => {
          setOpen((current) => !current);
        }}
      >
        Filter {kind === "provider" ? "Providers" : "Modules"}
      </button>
      <Filters
        value={value}
        kindLocked={kind}
        mobileOpen={open}
        onChange={(key, next) => {
          setValue((current) => ({ ...current, [key]: next }));
        }}
        onClear={() => {
          setValue({});
        }}
      />
    </>
  );
}

describe("Filters", () => {
  it("matches provider tier and category filtering accessibly", async () => {
    const user = userEvent.setup();
    const { container } = render(<FilterHarness />);
    await user.click(screen.getByRole("button", { name: "Filter Providers" }));
    const panel = screen.getByRole("complementary", {
      name: "Catalog filters",
    });
    expect(panel).toHaveClass("is-open");

    const tierGroup = screen.getByRole("group", { name: "Tier" });
    const official = within(tierGroup).getByRole("checkbox", {
      name: /^Official/,
    });
    expect(official).toBeChecked();
    await user.click(official);
    expect(official).not.toBeChecked();

    const categoryGroup = screen.getByRole("group", { name: "Category" });
    const networking = within(categoryGroup).getByRole("checkbox", {
      name: "Networking",
    });
    await user.click(networking);
    expect(networking).toBeChecked();

    await user.click(screen.getByRole("button", { name: "Clear Filters" }));
    expect(official).toBeChecked();
    expect(networking).not.toBeChecked();
    await user.click(screen.getByRole("button", { name: "Filter Providers" }));
    expect(panel).not.toHaveClass("is-open");

    const result = await axe.run(container, {
      rules: { "color-contrast": { enabled: false } },
    });
    expect(result.violations).toEqual([]);
  });

  it("filters modules by partner tier and multiple providers", async () => {
    const user = userEvent.setup();
    render(<FilterHarness kind="module" />);
    const tierGroup = screen.getByRole("group", { name: "Tier" });
    await user.click(
      within(tierGroup).getByRole("checkbox", { name: /^Partner/ }),
    );
    const providerGroup = screen.getByRole("group", { name: "Provider" });
    const aws = within(providerGroup).getByRole("checkbox", { name: "AWS" });
    const azure = within(providerGroup).getByRole("checkbox", {
      name: "Azure",
    });
    await user.click(aws);
    await user.click(azure);
    expect(aws).toBeChecked();
    expect(azure).toBeChecked();
  });
});
