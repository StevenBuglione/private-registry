import { lazy, type ReactElement, Suspense } from "react";
import {
  createBrowserRouter,
  Navigate,
  useLocation,
  useParams,
} from "react-router";
import { AppShell } from "./components/AppShell";
import { StatePanel } from "./components/StatePanel";
import type { PackageKind } from "./types";

const AdminSettingsPage = lazy(async () => ({
  default: (await import("./routes/AdminSettingsPage")).AdminSettingsPage,
}));
const CatalogPage = lazy(async () => ({
  default: (await import("./routes/CatalogPage")).CatalogPage,
}));
const HomePage = lazy(async () => ({
  default: (await import("./routes/HomePage")).HomePage,
}));
const PackageDetailPage = lazy(async () => ({
  default: (await import("./routes/PackageDetailPage")).PackageDetailPage,
}));

export const router = createBrowserRouter([
  {
    path: "/",
    element: <AppShell />,
    errorElement: <RouteError />,
    children: [
      { index: true, element: lazyRoute(<HomePage />) },
      { path: "admin", element: lazyRoute(<AdminSettingsPage />) },
      { path: "browse", element: lazyRoute(<CatalogPage />) },
      {
        path: "browse/providers",
        element: lazyRoute(<CatalogPage kind="provider" />),
      },
      {
        path: "browse/modules",
        element: lazyRoute(<CatalogPage kind="module" />),
      },
      { path: "namespaces/:namespace", element: lazyRoute(<CatalogPage />) },
      {
        path: "providers",
        element: lazyRoute(<CatalogPage kind="provider" />),
      },
      {
        path: "providers/:namespace/:name/:version?",
        element: lazyRoute(<PackageDetailPage kind="provider" />),
      },
      {
        path: "modules",
        element: lazyRoute(<CatalogPage kind="module" />),
      },
      {
        path: "modules/:namespace/:name/:target/:version?",
        element: lazyRoute(<PackageDetailPage kind="module" />),
      },
      {
        path: "modules/:namespace/:name/:target/:version/submodules/:moduleChild",
        element: lazyRoute(
          <PackageDetailPage kind="module" moduleChildKind="submodule" />,
        ),
      },
      {
        path: "modules/:namespace/:name/:target/:version/examples/:moduleChild",
        element: lazyRoute(
          <PackageDetailPage kind="module" moduleChildKind="example" />,
        ),
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

function lazyRoute(element: ReactElement) {
  return (
    <Suspense
      fallback={
        <section className="page-shell route-loading" aria-busy="true">
          <h1 className="sr-only">Registry</h1>
          <p className="sr-only" role="status">
            Loading page
          </p>
        </section>
      }
    >
      {element}
    </Suspense>
  );
}

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
