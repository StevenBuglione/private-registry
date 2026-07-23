import { createContext } from "react";
import type { RegistrySession } from "./types";

export interface RegistryContextValue {
  session: RegistrySession;
}

export const RegistryContext = createContext<RegistryContextValue | null>(null);
