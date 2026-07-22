import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import {
  ApiError,
  catalogEventsUrl,
  getCatalogPage,
  getHomepageSettings,
  getPackage,
  getPackageDocumentation,
  getPackageGovernance,
  getSession,
  updateHomepageSettings,
} from "./api";
import type {
  CatalogQuery,
  GovernanceRecord,
  HomepageSettingsUpdate,
  PackageKind,
} from "./types";

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

export function useHomepageSettings() {
  return useQuery({
    queryKey: ["homepage-settings"],
    queryFn: getHomepageSettings,
    retry: queryRetry,
    staleTime: 30_000,
  });
}

export function useUpdateHomepageSettings(csrfToken?: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (update: HomepageSettingsUpdate) =>
      updateHomepageSettings(update, csrfToken),
    onSuccess: async (settings) => {
      queryClient.setQueryData(["homepage-settings"], settings);
      await queryClient.invalidateQueries({ queryKey: ["homepage-settings"] });
    },
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
  target: string | undefined;
  version: string | undefined;
  apmId: string | undefined;
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
  documentPath?: string,
) {
  return useQuery({
    queryKey: ["package-documentation", identity, documentPath],
    queryFn: () =>
      getPackageDocumentation(
        identity.kind,
        identity.namespace,
        identity.name,
        identity.target,
        identity.version,
        identity.apmId,
        documentPath,
      ),
    initialData:
      documentPath !== undefined && documentPath.length > 0
        ? undefined
        : initial,
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
    return () => {
      eventSource.close();
    };
  }, [apmId, queryClient]);
}
