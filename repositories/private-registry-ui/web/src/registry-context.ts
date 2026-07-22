import { createContext } from "react";
import type { RegistrySession } from "./types";

export interface RegistryContextValue {
  session: RegistrySession;
  selectedApmId: string | undefined;
  setSelectedApmId: (value: string) => void;
}

export const RegistryContext = createContext<RegistryContextValue | null>(null);
