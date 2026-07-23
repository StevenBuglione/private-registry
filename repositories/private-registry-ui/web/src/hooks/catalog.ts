import { useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import type * as CatalogApiModule from "../api/catalog";
import { ApiError } from "../api/client";
import { runtimeConfig } from "../runtime-config";
import type { CatalogQuery, PackageKind, PackageSummary } from "../types";
import { queryRetry } from "./retry";

let catalogApiPromise: Promise<typeof CatalogApiModule> | undefined;
const loadCatalogApi = () => {
  catalogApiPromise ??= import("../api/catalog");
  return catalogApiPromise;
};
const MAX_FEATURED_PACKAGES = 6;

export function useCatalogPage(query: CatalogQuery) {
  return useQuery({
    queryKey: ["catalog", query],
    queryFn: async () => (await loadCatalogApi()).getCatalogPage(query),
    retry: queryRetry,
    staleTime: 20_000,
    placeholderData: (previous) => previous,
  });
}

export function useCatalogSuggestions(q: string) {
  return useQuery({
    queryKey: ["catalog-suggestions", q],
    queryFn: async () =>
      (await loadCatalogApi()).getCatalogPage({
        q,
        sort: "relevance",
        limit: 8,
      }),
    enabled: q.trim().length >= 2,
    retry: queryRetry,
    staleTime: 30_000,
  });
}

interface PackageIdentity {
  kind: PackageKind;
  namespace: string;
  name: string;
  target: string | undefined;
  version: string | undefined;
}

export function useFeaturedPackages(ids: string[]) {
  const identities = featuredPackageIdentities(ids);
  const queries = useQueries({
    queries: identities.map((identity) => ({
      queryKey: ["package", identity],
      queryFn: async () =>
        (await loadCatalogApi()).getPackage(
          identity.kind,
          identity.namespace,
          identity.name,
          identity.target,
        ),
      retry: queryRetry,
      staleTime: 30_000,
    })),
  });

  return {
    items: queries.flatMap((query) =>
      query.data === undefined ? [] : [query.data as PackageSummary],
    ),
    isPending: queries.some((query) => query.isPending),
    isError: queries.some(
      (query) => query.isError && !isApiNotFound(query.error),
    ),
  };
}

export function usePackage(identity: PackageIdentity) {
  return useQuery({
    queryKey: ["package", identity],
    queryFn: async () =>
      (await loadCatalogApi()).getPackage(
        identity.kind,
        identity.namespace,
        identity.name,
        identity.target,
        identity.version,
      ),
    retry: queryRetry,
  });
}

export function usePackageDocumentation(
  identity: PackageIdentity,
  initial?: string,
  documentPath?: string,
) {
  return useQuery({
    queryKey: ["package-documentation", identity, documentPath],
    queryFn: async () =>
      (await loadCatalogApi()).getPackageDocumentation(
        identity.kind,
        identity.namespace,
        identity.name,
        identity.target,
        identity.version,
        documentPath,
      ),
    initialData:
      documentPath !== undefined && documentPath.length > 0
        ? undefined
        : initial,
    retry: queryRetry,
  });
}

export function useCatalogEvents() {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!("EventSource" in window)) return;
    const eventSource = new EventSource(
      `${runtimeConfig().apiBaseUrl}/catalog/events`,
      {
        withCredentials: true,
      },
    );
    const refresh = () => {
      void queryClient.invalidateQueries({ queryKey: ["catalog"] });
      void queryClient.invalidateQueries({
        queryKey: ["catalog-suggestions"],
      });
      void queryClient.invalidateQueries({ queryKey: ["package"] });
      void queryClient.invalidateQueries({
        queryKey: ["package-documentation"],
      });
    };

    eventSource.addEventListener("catalog-change", refresh);
    eventSource.onmessage = refresh;
    return () => {
      eventSource.close();
    };
  }, [queryClient]);
}

function featuredPackageIdentities(ids: string[]): PackageIdentity[] {
  const identities = new Map<string, PackageIdentity>();
  for (const id of ids) {
    const parts = id.split("/");
    const kind = parts[0];
    const validProvider =
      kind === "provider" &&
      parts.length === 3 &&
      parts.every((part) => part.length > 0);
    const validModule =
      kind === "module" &&
      parts.length === 4 &&
      parts.every((part) => part.length > 0);
    if (!validProvider && !validModule) continue;
    identities.set(id, {
      kind,
      namespace: parts[1] ?? "",
      name: parts[2] ?? "",
      target: kind === "module" ? parts[3] : undefined,
      version: undefined,
    });
    if (identities.size === MAX_FEATURED_PACKAGES) break;
  }
  return [...identities.values()];
}

function isApiNotFound(error: unknown): boolean {
  if (error instanceof ApiError) return error.status === 404;
  return (
    typeof error === "object" &&
    error !== null &&
    "status" in error &&
    error.status === 404
  );
}
