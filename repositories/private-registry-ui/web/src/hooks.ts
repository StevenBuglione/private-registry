import { useEffect } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ApiError,
  catalogEventsUrl,
  getCatalogPage,
  getPackage,
  getPackageDocumentation,
  getPackageGovernance,
  getSession,
} from "./api";
import type { CatalogQuery, GovernanceRecord, PackageKind } from "./types";

const queryRetry = (failureCount: number, error: Error) =>
  error instanceof ApiError && error.status >= 500 && failureCount < 1;

export function useSession() {
  return useQuery({
    queryKey: ["session"],
    queryFn: getSession,
    retry: queryRetry,
    staleTime: 45_000,
  });
}

export function useCatalogPage(query: CatalogQuery) {
  return useQuery({
    queryKey: ["catalog", query],
    queryFn: () => getCatalogPage(query),
    retry: queryRetry,
    staleTime: 20_000,
    placeholderData: (previous) => previous,
  });
}

export function useCatalogSuggestions(q: string, apmId?: string) {
  return useQuery({
    queryKey: ["catalog-suggestions", q, apmId],
    queryFn: () =>
      getCatalogPage({
        q,
        apmId,
        approval: "approved",
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
  target?: string;
  version?: string;
  apmId?: string;
}

export function usePackage(identity: PackageIdentity) {
  return useQuery({
    queryKey: ["package", identity],
    queryFn: () =>
      getPackage(
        identity.kind,
        identity.namespace,
        identity.name,
        identity.target,
        identity.version,
        identity.apmId,
      ),
    retry: queryRetry,
  });
}

export function usePackageDocumentation(
  identity: PackageIdentity,
  initial?: string,
) {
  return useQuery({
    queryKey: ["package-documentation", identity],
    queryFn: () =>
      getPackageDocumentation(
        identity.kind,
        identity.namespace,
        identity.name,
        identity.target,
        identity.version,
        identity.apmId,
      ),
    initialData: initial,
    retry: queryRetry,
  });
}

export function usePackageGovernance(
  identity: PackageIdentity,
  initial?: GovernanceRecord,
) {
  return useQuery({
    queryKey: ["package-governance", identity],
    queryFn: () =>
      getPackageGovernance(
        identity.kind,
        identity.namespace,
        identity.name,
        identity.target,
        identity.version,
        identity.apmId,
      ),
    initialData: initial,
    retry: queryRetry,
  });
}

export function useCatalogEvents(apmId?: string) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!("EventSource" in window)) return;
    const eventSource = new EventSource(catalogEventsUrl(apmId), {
      withCredentials: true,
    });
    const refresh = () => {
      void queryClient.invalidateQueries({ queryKey: ["catalog"] });
      void queryClient.invalidateQueries({
        queryKey: ["catalog-suggestions"],
      });
      void queryClient.invalidateQueries({ queryKey: ["package"] });
      void queryClient.invalidateQueries({
        queryKey: ["package-documentation"],
      });
      void queryClient.invalidateQueries({ queryKey: ["package-governance"] });
    };

    eventSource.addEventListener("catalog-change", refresh);
    eventSource.onmessage = refresh;
    return () => eventSource.close();
  }, [apmId, queryClient]);
}
