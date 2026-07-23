import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createSyncCredential,
  getAdminDashboard,
  getAdminOperations,
  getAuditEvents,
  getSyncCredentials,
  getTrafficReport,
  revokeSyncCredential,
} from "../api/admin";
import type { CreateSyncCredential } from "../types";
import { queryRetry } from "./retry";

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
