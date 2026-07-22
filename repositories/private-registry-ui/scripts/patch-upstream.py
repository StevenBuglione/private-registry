#!/usr/bin/env python3
"""Apply small, deterministic hooks to the approved OpenTofu UI frontend.

The patch intentionally fails when upstream source no longer matches the reviewed
shape. That turns upstream drift into a review instead of silently producing a
partially integrated build.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"


def replace_once(relative: str, before: str, after: str, marker: str) -> None:
    path = APP / relative
    text = path.read_text()
    if marker in text:
        return
    if text.count(before) != 1:
        raise SystemExit(f"{relative}: expected reviewed upstream text exactly once")
    path.write_text(text.replace(before, after, 1))


replace_once(
    "src/query.ts",
    'export const api = ky.create({ prefix: import.meta.env.VITE_DATA_API_URL });',
    '''export let api = ky.create({ prefix: import.meta.env.VITE_DATA_API_URL });

export function configureApi(prefix: string): void {
  api = ky.create({ prefix });
}''',
    "export function configureApi",
)

replace_once(
    "src/main.tsx",
    '''import { queryClient } from "./query";
import { router } from "./router";''',
    '''import { configureApi, queryClient } from "./query";
import { loadRegistryRuntimeConfig } from "./enterprise/runtime-config";''',
    "loadRegistryRuntimeConfig",
)

replace_once(
    "src/main.tsx",
    '''createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AnnouncementBar />
      <RouterProvider router={router} />
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  </StrictMode>,
);''',
    '''async function bootstrap(): Promise<void> {
  const runtimeConfig = await loadRegistryRuntimeConfig();
  configureApi(runtimeConfig.dataApiUrl);
  const { router } = await import("./router");

  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <AnnouncementBar />
        <RouterProvider router={router} />
        <ReactQueryDevtools initialIsOpen={false} />
      </QueryClientProvider>
    </StrictMode>,
  );
}

void bootstrap().catch((error: unknown) => {
  console.error("Registry UI bootstrap failed", error);
  const root = document.getElementById("root");
  if (root)
    root.textContent = "The registry application could not be initialized.";
});''',
    "async function bootstrap",
)

# Upgrade app/ directories patched by an earlier revision that imported the
# router before runtime configuration. Fresh imports already have the desired
# form from the replacements above.
main_path = APP / "src/main.tsx"
main_text = main_path.read_text()
if "loadRegistryRuntimeConfig" in main_text:
    main_text = main_text.replace('import { router } from "./router";\n', "")
    if 'await import("./router")' not in main_text:
        bootstrap_marker = "  configureApi(runtimeConfig.dataApiUrl);\n"
        if main_text.count(bootstrap_marker) != 1:
            raise SystemExit("src/main.tsx: unable to upgrade runtime-first router bootstrap")
        main_text = main_text.replace(
            bootstrap_marker,
            bootstrap_marker + '  const { router } = await import("./router");\n',
            1,
        )
    main_path.write_text(main_text)

replace_once(
    "src/routes/Module/index.tsx",
    '''import {
  ModuleVersionsSidebarBlock,
  ModuleVersionsSidebarBlockSkeleton,
} from "./components/VersionsSidebarBlock";''',
    '''import {
  ModuleVersionsSidebarBlock,
  ModuleVersionsSidebarBlockSkeleton,
} from "./components/VersionsSidebarBlock";
import { ModuleEnterprisePanel } from "@/enterprise/ModuleEnterprisePanel";''',
    "ModuleEnterprisePanel",
)
replace_once(
    "src/routes/Module/index.tsx",
    '''          <ModuleHeader />
          <ModuleVersionInfo />''',
    '''          <ModuleHeader />
          <ModuleVersionInfo />
          <ModuleEnterprisePanel />''',
    "<ModuleEnterprisePanel />",
)

replace_once(
    "src/routes/Provider/index.tsx",
    '''import {
  TableOfContents,
  TableOfContentsSkeleton,
} from "./components/TableOfContents";''',
    '''import {
  TableOfContents,
  TableOfContentsSkeleton,
} from "./components/TableOfContents";
import { ProviderEnterprisePanel } from "@/enterprise/ProviderEnterprisePanel";''',
    "ProviderEnterprisePanel",
)
replace_once(
    "src/routes/Provider/index.tsx",
    '''            <ProviderHeader />
            <ProviderVersionInfo />''',
    '''            <ProviderHeader />
            <ProviderVersionInfo />
            <ProviderEnterprisePanel />''',
    "<ProviderEnterprisePanel />",
)

print("Applied reviewed runtime and enterprise panel hooks")
