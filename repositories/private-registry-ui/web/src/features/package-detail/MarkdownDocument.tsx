import {
  CheckIcon,
  ClipboardIcon,
  InfoIcon,
  LinkSimpleIcon,
  WarningIcon,
} from "@phosphor-icons/react";
import { Children, isValidElement, type ReactNode, useState } from "react";
import ReactMarkdown from "react-markdown";
import rehypeSanitize from "rehype-sanitize";
import remarkGfm from "remark-gfm";
import { slugText } from "./model";

export function MarkdownDocument({
  docs,
  className = "",
}: {
  docs: string;
  className?: string;
}) {
  return (
    <article className={`documentation ${className}`.trim()} id="overview">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeSanitize]}
        components={{
          h1: ({ children }) => <h1 id={slugText(children)}>{children}</h1>,
          h2: ({ children }) => {
            const id = slugText(children);
            return (
              <h2 id={id}>
                <a className="heading-self-link" href={`#${id}`}>
                  {children}
                  <LinkSimpleIcon aria-hidden="true" size={15} />
                </a>
              </h2>
            );
          },
          h3: ({ children }) => {
            const id = slugText(children);
            return (
              <h3 id={id}>
                <a className="heading-self-link" href={`#${id}`}>
                  {children}
                  <LinkSimpleIcon aria-hidden="true" size={14} />
                </a>
              </h3>
            );
          },
          p: MarkdownParagraph,
          pre: MarkdownPre,
        }}
      >
        {docs}
      </ReactMarkdown>
    </article>
  );
}

function MarkdownParagraph({ children }: { children?: ReactNode }) {
  const parts = Children.toArray(children);
  const leading = typeof parts[0] === "string" ? parts[0] : "";
  const isInfo = /^\s*->\s*/.test(leading);
  const isWarning = /^\s*~>\s*/.test(leading);
  if (!isInfo && !isWarning) return <p>{children}</p>;
  const rest = [leading.replace(/^\s*(?:->|~>)\s*/, ""), ...parts.slice(1)];
  return (
    <aside
      className={`docs-callout ${isWarning ? "warning" : "info"}`}
      role="note"
    >
      {isWarning ? (
        <WarningIcon aria-hidden="true" size={18} weight="fill" />
      ) : (
        <InfoIcon aria-hidden="true" size={18} weight="fill" />
      )}
      <p>{rest}</p>
    </aside>
  );
}

function MarkdownPre({ children }: { children?: ReactNode }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(reactNodeText(children));
    setCopied(true);
    window.setTimeout(() => {
      setCopied(false);
    }, 1800);
  };
  return (
    <div className="markdown-code-block">
      <button type="button" onClick={() => void copy()}>
        {copied ? <CheckIcon size={14} /> : <ClipboardIcon size={14} />}
        {copied ? "Copied" : "Copy"}
      </button>
      <pre>{children}</pre>
    </div>
  );
}

function reactNodeText(node: ReactNode): string {
  return Children.toArray(node)
    .map((child) => {
      if (typeof child === "string" || typeof child === "number") {
        return String(child);
      }
      if (isValidElement<{ children?: ReactNode }>(child)) {
        return reactNodeText(child.props.children);
      }
      return "";
    })
    .join("");
}
