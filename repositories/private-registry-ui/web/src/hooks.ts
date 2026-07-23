import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import {
  ApiError,
  catalogEventsUrl,
  createSyncCredential,
  getAdminDashboard,
  getAdminOperations,
  getAuditEvents,
  getCatalogPage,
  getHomepageSettings,
  getPackage,
  getPackageDocumentation,
  getSession,
  getSyncCredentials,
  getTrafficReport,
  revokeSyncCredential,
  updateHomepageSettings,
} from "./api";
import type {
  CatalogQuery,
  CreateSyncCredential,
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

export function useAdminDashboard() {
  return useQuery({
    queryKey: ["admin", "dashboard"],
    queryFn: getAdminDashboard,
    retry: queryRetry,
    refetchInterval: 15_000,
  });
}

export function useTrafficReport(days: number) {
  return useQuery({
    queryKey: ["admin", "traffic", days],
    queryFn: () => getTrafficReport(days),
    retry: queryRetry,
    refetchInterval: 30_000,
  });
}

export function useAdminOperations() {
  return useQuery({
    queryKey: ["admin", "operations"],
    queryFn: getAdminOperations,
    retry: queryRetry,
    refetchInterval: 15_000,
  });
}

export function useAuditEvents() {
  return useQuery({
    queryKey: ["admin", "audit-events"],
    queryFn: getAuditEvents,
    retry: queryRetry,
    refetchInterval: 30_000,
  });
}

export function useSyncCredentials() {
  return useQuery({
    queryKey: ["admin", "sync-credentials"],
    queryFn: getSyncCredentials,
    retry: queryRetry,
  });
}

export function useCreateSyncCredential(csrfToken?: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (value: CreateSyncCredential) =>
      createSyncCredential(value, csrfToken),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["admin", "sync-credentials"],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin", "audit-events"],
      });
    },
  });
}

export function useRevokeSyncCredential(csrfToken?: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => revokeSyncCredential(id, csrfToken),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["admin", "sync-credentials"],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin", "audit-events"],
      });
    },
  });
}

export function useCatalogSuggestions(q: string) {
  return useQuery({
    queryKey: ["catalog-suggestions", q],
    queryFn: () =>
      getCatalogPage({
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
    const eventSource = new EventSource(catalogEventsUrl(), {
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
    };

    eventSource.addEventListener("catalog-change", refresh);
    eventSource.onmessage = refresh;
    return () => {
      eventSource.close();
    };
  }, [queryClient]);
}
