import "katex/dist/katex.min.css";
import { useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import rehypeHighlight from "rehype-highlight";
import { Check, Copy } from "lucide-react";
import { MermaidBlock } from "./MermaidBlock";

function nodeToText(node: React.ReactNode): string {
  if (typeof node === "string" || typeof node === "number") return String(node);
  if (Array.isArray(node)) return node.map(nodeToText).join("");
  return "";
}

function childClassName(children: React.ReactNode): string {
  const child = Array.isArray(children) ? children[0] : children;
  if (child && typeof child === "object" && "props" in child) {
    return (child as React.ReactElement<{ className?: string }>).props.className ?? "";
  }
  return "";
}

function Code({ className, children, ...props }: React.ComponentProps<"code">) {
  const match = /language-(\w+)/.exec(className ?? "");
  if (match?.[1] === "mermaid") {
    return <MermaidBlock chart={nodeToText(children).replace(/\n$/, "")} />;
  }
  return <code className={className} {...props}>{children}</code>;
}

function Pre({ children }: React.ComponentProps<"pre">) {
  const ref = useRef<HTMLPreElement>(null);
  const [copied, setCopied] = useState(false);
  const lang = /language-(\w+)/.exec(childClassName(children))?.[1];

  const copy = async () => {
    const text = ref.current?.textContent ?? "";
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch { /* ignore */ }
  };

  return (
    <div className="not-prose group relative my-4 overflow-hidden rounded-md border border-black/10 bg-[#f6f8fa] dark:border-white/10 dark:bg-[#0d1117]">
      <div className="flex items-center justify-between border-b border-black/10 bg-[#eaeef2] px-3 py-1 text-[11px] text-muted-foreground dark:border-white/10 dark:bg-[#161b22]">
        <span>{lang ?? "text"}</span>
        <button type="button" onClick={copy} className="inline-flex items-center gap-1 transition-colors hover:text-foreground">
          {copied ? <><Check className="size-3" />已复制</> : <><Copy className="size-3" />复制</>}
        </button>
      </div>
      <pre ref={ref} className="overflow-x-auto p-3 text-xs leading-relaxed">
        {children}
      </pre>
    </div>
  );
}

export function MarkdownRenderer({ content, className }: { content: string; className?: string }) {
  return (
    <div className={`prose prose-sm dark:prose-invert max-w-none ${className ?? ""}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[
          [rehypeKatex, { throwOnError: false }],
          [rehypeHighlight, { ignoreMissing: true }],
        ]}
        components={{ code: Code, pre: Pre }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
