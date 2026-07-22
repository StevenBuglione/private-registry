import { useState } from "react";
import { StackIcon } from "@phosphor-icons/react";

export function RegistryMark({ compact = false }: { compact?: boolean }) {
  const [failed, setFailed] = useState(false);
  if (failed) {
    return (
      <span className="brand-mark-fallback" aria-hidden="true">
        <StackIcon size={compact ? 24 : 28} weight="regular" />
      </span>
    );
  }
  return (
    <img
      className={compact ? "brand-mark compact" : "brand-mark"}
      src="/assets/registry-mark.png"
      alt=""
      width={compact ? 28 : 34}
      height={compact ? 28 : 34}
      onError={() => setFailed(true)}
    />
  );
}
