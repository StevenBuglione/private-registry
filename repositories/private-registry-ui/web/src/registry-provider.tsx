import { type ReactNode, useMemo } from "react";
import { RegistryContext } from "./registry-context";
import type { RegistrySession } from "./types";

export function RegistryProvider({
  session,
  children,
}: {
  session: RegistrySession;
  children: ReactNode;
}) {
  const value = useMemo(
    () => ({
      session,
      selectedApmId: undefined,
    }),
    [session],
  );

  return (
    <RegistryContext.Provider value={value}>
      {children}
    </RegistryContext.Provider>
  );
}
