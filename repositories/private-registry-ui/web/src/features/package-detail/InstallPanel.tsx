import { CheckIcon, ClipboardIcon } from "@phosphor-icons/react";
import { useState } from "react";
import type { PackageKind } from "../../types";
import { hasText } from "../../utils";

export function InstallPanel({
  snippet,
  kind,
  artifactLabel,
}: {
  snippet: string;
  kind: PackageKind;
  artifactLabel?: string;
}) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(snippet);
    setCopied(true);
    window.setTimeout(() => {
      setCopied(false);
    }, 1800);
  };
  return (
    <section className="source-install-card">
      <h2>
        {kind === "provider"
          ? "How to use this provider"
          : "Provision Instructions"}
      </h2>
      <p>
        {kind === "provider" ? (
          <>
            To install this provider, copy and paste this code into your
            Terraform configuration. Then, run <code>terraform init</code>.
          </>
        ) : (
          <>
            Copy and paste into your Terraform configuration, insert the
            variables, and run <code>terraform init</code>:
          </>
        )}
      </p>
      {kind === "provider" ? (
        <div className="source-install-code">
          <strong>Terraform 0.13+</strong>
          <pre>
            <code>{snippet}</code>
          </pre>
        </div>
      ) : (
        <pre>
          <code>{snippet}</code>
        </pre>
      )}
      <button type="button" onClick={() => void copy()}>
        {copied ? <CheckIcon size={16} /> : <ClipboardIcon size={16} />}
        {copied ? "Copied" : kind === "module" ? "Copy configuration" : "Copy"}
      </button>
      {hasText(artifactLabel) ? (
        <small className="artifact-source-note">
          Artifact source: <code>{artifactLabel}</code>
        </small>
      ) : null}
    </section>
  );
}
