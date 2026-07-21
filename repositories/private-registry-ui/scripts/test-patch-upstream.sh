#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d /tmp/private-registry-ui-patch-test.XXXXXX)"
cleanup() { rm -rf -- "${TMP}"; }
trap cleanup EXIT

mkdir -p "${TMP}/scripts" "${TMP}/overlays/src"
cp "${ROOT}/scripts/apply-overlays.sh" "${ROOT}/scripts/patch-upstream.py" "${TMP}/scripts/"
cp -a "${ROOT}/overlays/src/enterprise" "${TMP}/overlays/src/"
mkdir -p \
  "${TMP}/app/src/routes/Module" \
  "${TMP}/app/src/routes/Provider"

cat > "${TMP}/app/package.json" <<'JSON'
{"name":"private-registry-ui-patch-fixture","private":true}
JSON
cat > "${TMP}/app/src/query.ts" <<'TS'
import { QueryClient } from "@tanstack/react-query";
import ky from "ky";
export const queryClient = new QueryClient();
export const api = ky.create({ prefix: import.meta.env.VITE_DATA_API_URL });
TS
cat > "${TMP}/app/src/main.tsx" <<'TSX'
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router/dom";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { QueryClientProvider } from "@tanstack/react-query";

import { queryClient } from "./query";
import { router } from "./router";
import { AnnouncementBar } from "./components/AnnouncementBar";
import "./index.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AnnouncementBar />
      <RouterProvider router={router} />
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  </StrictMode>,
);
TSX
cat > "${TMP}/app/src/routes/Module/index.tsx" <<'TSX'
import {
  ModuleVersionsSidebarBlock,
  ModuleVersionsSidebarBlockSkeleton,
} from "./components/VersionsSidebarBlock";
function Fixture() {
  return <>
          <ModuleHeader />
          <ModuleVersionInfo />
  </>;
}
TSX
cat > "${TMP}/app/src/routes/Provider/index.tsx" <<'TSX'
import {
  TableOfContents,
  TableOfContentsSkeleton,
} from "./components/TableOfContents";
function Fixture() {
  return <>
            <ProviderHeader />
            <ProviderVersionInfo />
  </>;
}
TSX

(
  cd "${TMP}"
  ./scripts/apply-overlays.sh >/dev/null
  ./scripts/apply-overlays.sh >/dev/null
)

grep -q 'export function configureApi' "${TMP}/app/src/query.ts"
grep -q 'loadRegistryRuntimeConfig' "${TMP}/app/src/main.tsx"
grep -q '<ModuleEnterprisePanel />' "${TMP}/app/src/routes/Module/index.tsx"
grep -q '<ProviderEnterprisePanel />' "${TMP}/app/src/routes/Provider/index.tsx"
grep -q 'dataApiUrl: "/registry/docs/"' "${TMP}/app/src/enterprise/runtime-config.ts"

echo 'OpenTofu UI patch smoke test passed'
