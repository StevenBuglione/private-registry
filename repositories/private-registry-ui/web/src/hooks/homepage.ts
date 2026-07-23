import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getHomepageSettings, updateHomepageSettings } from "../api/homepage";
import type { HomepageSettingsUpdate } from "../types";
import { queryRetry } from "./retry";

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
