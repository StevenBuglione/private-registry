import {
  ArrowClockwiseIcon,
  LockKeyIcon,
  MagnifyingGlassIcon,
  ShieldWarningIcon,
  WifiSlashIcon,
} from "@phosphor-icons/react";

type StateKind =
  | "loading"
  | "empty"
  | "no-access"
  | "revoked"
  | "identity-error"
  | "api-error"
  | "not-found";

const copy: Record<StateKind, { title: string; description: string }> = {
  loading: {
    title: "Loading your registry",
    description: "We’re finding providers and modules available to you.",
  },
  empty: {
    title: "No packages match these filters",
    description: "Try a broader search or clear one of the active filters.",
  },
  "no-access": {
    title: "No registry access yet",
    description:
      "You’re signed in, but none of your Entra groups currently grant access to a registry APM.",
  },
  revoked: {
    title: "Registry access was revoked",
    description:
      "Your identity is valid, but your current groups no longer grant access to this registry.",
  },
  "identity-error": {
    title: "Identity service is unavailable",
    description:
      "We couldn’t verify your Entra memberships. Access stays closed until verification succeeds.",
  },
  "api-error": {
    title: "The registry is temporarily unavailable",
    description:
      "We couldn’t load catalog data. Your access settings have not changed.",
  },
  "not-found": {
    title: "Package not found",
    description:
      "The package may not exist or may not be available to your account.",
  },
};

export function StatePanel({
  kind,
  action,
  actionLabel,
}: {
  kind: StateKind;
  action?: () => void;
  actionLabel?: string;
}) {
  const Icon = {
    loading: ArrowClockwiseIcon,
    empty: MagnifyingGlassIcon,
    "no-access": LockKeyIcon,
    revoked: ShieldWarningIcon,
    "identity-error": WifiSlashIcon,
    "api-error": WifiSlashIcon,
    "not-found": MagnifyingGlassIcon,
  }[kind];
  return (
    <section className="state-panel" aria-live="polite">
      <span className={`state-icon ${kind === "loading" ? "is-spinning" : ""}`}>
        <Icon size={30} weight="regular" aria-hidden="true" />
      </span>
      <h1>{copy[kind].title}</h1>
      <p>{copy[kind].description}</p>
      {action !== undefined ? (
        <button
          className="button button-primary"
          type="button"
          onClick={action}
        >
          {actionLabel ?? "Try again"}
        </button>
      ) : null}
    </section>
  );
}
