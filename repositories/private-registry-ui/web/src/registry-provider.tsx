import { type ReactNode, useEffect, useMemo, useState } from "react";
import { RegistryContext } from "./registry-context";
import type { RegistrySession } from "./types";

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
    if (selectedApmId !== undefined && selectedApmId.length > 0) {
      localStorage.setItem(storageKey, selectedApmId);
    }
  }, [selectedApmId, storageKey]);

  const value = useMemo(
    () => ({
      session,
      selectedApmId,
      setSelectedApmId: (value: string) => {
        setSelectedApmId(value.length > 0 ? value : undefined);
      },
    }),
    [session, selectedApmId],
  );

  return (
    <RegistryContext.Provider value={value}>
      {children}
    </RegistryContext.Provider>
  );
}
