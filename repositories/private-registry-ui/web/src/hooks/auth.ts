import { useQuery } from "@tanstack/react-query";
import { getSession } from "../api/auth";
import { queryRetry } from "./retry";

export function useSession() {
  return useQuery({
    queryKey: ["session"],
    queryFn: getSession,
    retry: queryRetry,
    staleTime: 45_000,
  });
}
