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
  const [copied, setCopied] = useState(false);
  const snippet = useMemo(() => {
    if (kind === "module") {
      return `module "${localName}" {\n  source  = "${source}"\n  version = "${version}"\n}`;
    }
    return `terraform {\n  required_providers {\n    ${localName} = {\n      source  = "${source}"\n      version = "${version}"\n    }\n  }\n}`;
  }, [kind, localName, source, version]);

  async function copy(): Promise<void> {
    await navigator.clipboard.writeText(snippet);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 2000);
  }

  return (
    <section aria-labelledby="registry-usage-heading">
      <div className="flex items-center justify-between gap-4">
        <h2 id="registry-usage-heading">Usage</h2>
        <button type="button" onClick={copy} aria-live="polite">
          {copied ? "Copied" : "Copy"}
        </button>
      </div>
      <pre tabIndex={0}>
        <code>{snippet}</code>
      </pre>
    </section>
  );
}
