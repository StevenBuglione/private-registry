import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import axe from "axe-core";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { type FilterState, Filters } from "./Filters";

function FilterHarness() {
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
        Filter Providers
      </button>
      <Filters
        value={value}
        kindLocked="provider"
        mobileOpen={open}
        onMobileToggle={() => {
          setOpen(false);
        }}
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
  it("opens the mobile filter region and updates catalog facets accessibly", async () => {
    const user = userEvent.setup();
    const { container } = render(<FilterHarness />);
    await user.click(screen.getByRole("button", { name: "Filter Providers" }));
    const panel = screen.getByRole("complementary", {
      name: "Catalog filters",
    });
    expect(panel).toHaveClass("is-open");
    const approvalGroup = screen.getByRole("group", { name: "Approval" });
    const approved = within(approvalGroup).getByRole("checkbox", {
      name: "Approved",
    });
    await user.click(approved);
    expect(approved).toBeChecked();
    await user.click(approved);
    expect(approved).not.toBeChecked();

    await user.click(
      within(screen.getByRole("group", { name: "Lifecycle" })).getByRole(
        "checkbox",
        { name: "Maintenance" },
      ),
    );
    await user.click(
      within(screen.getByRole("group", { name: "Risk" })).getByRole(
        "checkbox",
        { name: "High" },
      ),
    );
    await user.click(screen.getByRole("button", { name: "Clear all" }));
    await user.click(screen.getByRole("button", { name: "Close filters" }));
    expect(panel).not.toHaveClass("is-open");

    const result = await axe.run(container, {
      rules: { "color-contrast": { enabled: false } },
    });
    expect(result.violations).toEqual([]);
  });
});
