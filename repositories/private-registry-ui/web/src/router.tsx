import {
  createBrowserRouter,
  Navigate,
  useLocation,
  useParams,
} from "react-router";
import { AppShell } from "./components/AppShell";
import { StatePanel } from "./components/StatePanel";
import { CatalogPage } from "./routes/CatalogPage";
import { HomePage } from "./routes/HomePage";
import { PackageDetailPage } from "./routes/PackageDetailPage";
import type { PackageKind } from "./types";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <AppShell />,
    errorElement: <RouteError />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "browse", element: <CatalogPage /> },
      { path: "providers", element: <CatalogPage kind="provider" /> },
      {
        path: "providers/:namespace/:name/:version?",
        element: <PackageDetailPage kind="provider" />,
      },
      { path: "modules", element: <CatalogPage kind="module" /> },
      {
        path: "modules/:namespace/:name/:target/:version?",
        element: <PackageDetailPage kind="module" />,
      },
      {
        path: "modules/:namespace/:name/:target/:version/submodules/:moduleChild",
        element: (
          <PackageDetailPage kind="module" moduleChildKind="submodule" />
        ),
      },
      {
        path: "modules/:namespace/:name/:target/:version/examples/:moduleChild",
        element: <PackageDetailPage kind="module" moduleChildKind="example" />,
      },
      { path: "docs", element: <Navigate replace to="/" /> },
      {
        path: "provider/:namespace/:name/:version?/*",
        element: <LegacyPackageRedirect kind="provider" />,
      },
      {
        path: "module/:namespace/:name/:target/:version?/*",
        element: <LegacyPackageRedirect kind="module" />,
      },
      { path: "*", element: <StatePanel kind="not-found" /> },
    ],
  },
]);

export function LegacyPackageRedirect({ kind }: { kind: PackageKind }) {
  const params = useParams();
  const location = useLocation();
  const parts = [
    kind === "provider" ? "providers" : "modules",
    params["namespace"],
    params["name"],
    kind === "module" ? params["target"] : undefined,
    params["version"],
  ].filter(Boolean);
  return (
    <Navigate
      replace
      to={`/${parts.join("/")}${location.search}${location.hash}`}
    />
  );
}

function RouteError() {
  return (
    <div className="page-shell">
      <StatePanel
        kind="api-error"
        action={() => {
          window.location.reload();
        }}
      />
    </div>
  );
}
