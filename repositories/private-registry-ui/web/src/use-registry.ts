import { useContext } from "react";
import { RegistryContext, type RegistryContextValue } from "./registry-context";

export function useRegistry(): RegistryContextValue {
  const value = useContext(RegistryContext);
  if (value === null) {
    throw new Error("useRegistry must be used inside RegistryProvider");
  }
  return value;
}
