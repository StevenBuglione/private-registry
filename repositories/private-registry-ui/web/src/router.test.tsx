import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { describe, expect, it } from "vitest";
import { LegacyPackageRedirect } from "./router";

function LocationProbe() {
  const location = useLocation();
  return (
    <span>
      {location.pathname}
      {location.search}
    </span>
  );
}

describe("legacy package redirects", () => {
  it("redirects singular provider URLs to the canonical plural route", async () => {
    render(
      <MemoryRouter
        initialEntries={["/provider/platform/aws/6.5.0?tab=documentation"]}
      >
        <Routes>
          <Route
            path="provider/:namespace/:name/:version?/*"
            element={<LegacyPackageRedirect kind="provider" />}
          />
          <Route
            path="providers/:namespace/:name/:version"
            element={<LocationProbe />}
          />
        </Routes>
      </MemoryRouter>,
    );
    expect(
      await screen.findByText(
        "/providers/platform/aws/6.5.0?tab=documentation",
      ),
    ).toBeInTheDocument();
  });
});
