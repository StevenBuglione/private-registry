import { InfoIcon, LinkSimpleIcon, WarningIcon } from "@phosphor-icons/react";
import {
  Children,
  isValidElement,
  type ReactNode,
  useMemo,
  useState,
} from "react";
import ReactMarkdown from "react-markdown";
import rehypeSanitize from "rehype-sanitize";
import remarkGfm from "remark-gfm";
import { slugText } from "./model";

const TRAILING_NEWLINE = /\n$/;
const LANGUAGE_CLASS = /\blanguage-([\w-]+)\b/;
const LANGUAGE_ALIASES: Readonly<Record<string, string>> = {
  terraform: "hcl",
  tf: "hcl",
};
const HCL_LANGUAGES = new Set(["hcl"]);
const HCL_KEYWORDS = new Set([
  "data",
  "dynamic",
  "for",
  "if",
  "in",
  "locals",
  "module",
  "output",
  "provider",
  "resource",
  "terraform",
  "variable",
]);
const HCL_TOKEN =
  /\/\*[\s\S]*?\*\/|\/\/[^\n]*|#[^\n]*|"(?:\\.|[^"\\])*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|[A-Za-z_][\w-]*|=>|==|!=|>=|<=|&&|\|\||[=?:+*/%<>!-]|[{}[\](),.]|\s+|./g;
const HCL_NUMBER = /^-?\d/;
const HCL_WORD = /^[A-Za-z_]/;
const HCL_OPERATOR = /^(?:=>|==|!=|>=|<=|&&|\|\||[=?:+*/%<>!-])$/;
const HCL_PUNCTUATION = /^[{}[\](),.]$/;

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
  const code = reactNodeText(children).replace(TRAILING_NEWLINE, "");
  const language = markdownLanguage(children);
  const tokens = useMemo(
    () => highlightedCode(code, language),
    [code, language],
  );
  const copy = async () => {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    window.setTimeout(() => {
      setCopied(false);
    }, 1800);
  };
  return (
    <div className="markdown-code-block">
      <button type="button" onClick={() => void copy()}>
        {copied ? "Copied" : "Copy"}
      </button>
      <pre className={language ? `language-${language}` : undefined}>
        <code className={language ? `language-${language}` : undefined}>
          {tokens}
        </code>
      </pre>
    </div>
  );
}

function markdownLanguage(node: ReactNode): string {
  const codeElement = Children.toArray(node).find(
    (child) =>
      isValidElement<{ className?: string }>(child) &&
      typeof child.props.className === "string",
  );
  if (!isValidElement<{ className?: string }>(codeElement)) return "";
  const match = LANGUAGE_CLASS.exec(codeElement.props.className ?? "");
  const language = match?.[1]?.toLowerCase() ?? "";
  return LANGUAGE_ALIASES[language] ?? language;
}

function highlightedCode(code: string, language: string): ReactNode {
  if (!HCL_LANGUAGES.has(language)) return code;
  return tokenizeHcl(code).map((token, index) =>
    token.type === undefined ? (
      token.text
    ) : (
      <span className={`token ${token.type}`} key={String(index)}>
        {token.text}
      </span>
    ),
  );
}

interface HclToken {
  text: string;
  type?: string;
}

function tokenizeHcl(code: string): HclToken[] {
  return Array.from(code.matchAll(HCL_TOKEN), (match) => {
    const text = match[0];
    const type = hclTokenType(text, code.slice(match.index + text.length));
    return type === undefined ? { text } : { text, type };
  });
}

function hclTokenType(text: string, remainder: string): string | undefined {
  if (text.startsWith("#") || text.startsWith("//") || text.startsWith("/*")) {
    return "comment";
  }
  if (text.startsWith('"')) return "string";
  if (HCL_NUMBER.test(text)) return "number";
  if (text === "true" || text === "false" || text === "null") return "boolean";
  if (HCL_KEYWORDS.has(text)) return "keyword";
  if (HCL_WORD.test(text) && /^\s*=/.test(remainder)) return "property";
  if (HCL_OPERATOR.test(text)) return "operator";
  if (HCL_PUNCTUATION.test(text)) return "punctuation";
  return undefined;
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
