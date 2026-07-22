import { useMemo, useState } from "react";

type Props = {
  kind: "module" | "provider";
  localName: string;
  source: string;
  version: string;
};

export function RegistrySourceSnippet({
  kind,
  localName,
  source,
  version,
}: Props) {
  const [copyStatus, setCopyStatus] = useState<"idle" | "copied" | "error">(
    "idle",
  );
  const snippet = useMemo(() => {
    if (kind === "module") {
      return `module "${localName}" {\n  source  = "${source}"\n  version = "${version}"\n}`;
    }
    return `terraform {\n  required_providers {\n    ${localName} = {\n      source  = "${source}"\n      version = "${version}"\n    }\n  }\n}`;
  }, [kind, localName, source, version]);

  async function copy(): Promise<void> {
    setCopyStatus("copied");
    window.setTimeout(() => setCopyStatus("idle"), 2000);
    try {
      try {
        const clipboardWrite = navigator.clipboard?.writeText(snippet);
        if (!clipboardWrite) throw new Error("Clipboard API is unavailable");
        await Promise.race([
          clipboardWrite,
          new Promise<never>((_, reject) =>
            window.setTimeout(
              () => reject(new Error("Clipboard API timed out")),
              250,
            ),
          ),
        ]);
      } catch {
        const textarea = document.createElement("textarea");
        textarea.value = snippet;
        textarea.setAttribute("readonly", "");
        textarea.style.position = "fixed";
        textarea.style.opacity = "0";
        document.body.appendChild(textarea);
        textarea.select();
        const copied = document.execCommand("copy");
        textarea.remove();
        if (!copied) throw new Error("Clipboard copy is unavailable");
      }
    } catch {
      setCopyStatus("error");
    }
  }

  return (
    <section aria-labelledby="registry-usage-heading">
      <div className="flex items-center justify-between gap-4">
        <h2 id="registry-usage-heading">Usage</h2>
        <button type="button" onClick={copy} aria-live="polite">
          {copyStatus === "copied"
            ? "Copied"
            : copyStatus === "error"
              ? "Copy unavailable"
              : "Copy"}
        </button>
      </div>
      <pre tabIndex={0}>
        <code>{snippet}</code>
      </pre>
    </section>
  );
}
