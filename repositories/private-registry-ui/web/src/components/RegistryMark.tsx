import { StackIcon } from "@phosphor-icons/react";
import { useState } from "react";

export function RegistryMark({ compact = false }: { compact?: boolean }) {
  const [failed, setFailed] = useState(false);
  if (failed) {
    return (
      <span
        className={
          compact
            ? "brand-mark-frame brand-mark-fallback compact"
            : "brand-mark-frame brand-mark-fallback"
        }
        aria-hidden="true"
      >
        <StackIcon size={compact ? 24 : 28} weight="regular" />
      </span>
    );
  }
  return (
    <span
      className={compact ? "brand-mark-frame compact" : "brand-mark-frame"}
      aria-hidden="true"
    >
      <img
        className={compact ? "brand-mark compact" : "brand-mark"}
        src="/assets/registry-mark.png"
        alt=""
        width={compact ? 22 : 25}
        height={compact ? 22 : 25}
        onError={() => {
          setFailed(true);
        }}
      />
    </span>
  );
}
