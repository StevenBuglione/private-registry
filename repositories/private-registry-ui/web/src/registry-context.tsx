/* eslint-disable react-refresh/only-export-components */
import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import type { RegistrySession } from "./types";

interface RegistryContextValue {
  session: RegistrySession;
  selectedApmId?: string;
  setSelectedApmId: (value: string) => void;
}

const RegistryContext = createContext<RegistryContextValue | null>(null);

export function RegistryProvider({
  session,
  children,
}: {
  session: RegistrySession;
  children: ReactNode;
}) {
  const storageKey = `registry.apm.${session.subject || "user"}`;
  const [selectedApmId, setSelectedApmId] = useState<string | undefined>(() => {
    const stored = localStorage.getItem(storageKey);
    return session.apms.some((apm) => apm.id === stored)
      ? (stored ?? undefined)
      : session.apms[0]?.id;
  });

  useEffect(() => {
    if (selectedApmId) localStorage.setItem(storageKey, selectedApmId);
  }, [selectedApmId, storageKey]);

  const value = useMemo(
    () => ({
      session,
      selectedApmId,
      setSelectedApmId: (value: string) => setSelectedApmId(value || undefined),
    }),
    [session, selectedApmId],
  );

  return (
    <RegistryContext.Provider value={value}>
      {children}
    </RegistryContext.Provider>
  );
}

export function useRegistry(): RegistryContextValue {
  const value = useContext(RegistryContext);
  if (!value)
    throw new Error("useRegistry must be used inside RegistryProvider");
  return value;
}
