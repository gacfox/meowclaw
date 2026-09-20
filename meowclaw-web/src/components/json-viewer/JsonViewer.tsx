import { useMemo, useState, type ReactNode } from "react";
import { Braces, Check, ChevronsDownUp, ChevronsUpDown, Copy } from "lucide-react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import { highlightJson } from "./highlight-json";
import { JsonTree } from "./JsonTree";

type ViewMode = "text" | "tree";

function ModeButton({ active, disabled, onClick, children }: {
  active: boolean;
  disabled?: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "rounded px-2 py-0.5 text-[11px] leading-4 transition-colors",
        active
          ? "bg-white text-foreground shadow-sm dark:bg-[#21262d]"
          : "text-muted-foreground hover:text-foreground",
        disabled && "cursor-not-allowed opacity-40 hover:text-muted-foreground",
      )}
    >
      {children}
    </button>
  );
}

function ToolButton({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 transition-colors hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10"
    >
      {children}
    </button>
  );
}

interface JsonViewerProps {
  raw: string | null;
  maxHeightClass?: string;
}

export function JsonViewer({ raw, maxHeightClass = "max-h-96" }: JsonViewerProps) {
  const [mode, setMode] = useState<ViewMode>("text");
  const [copied, setCopied] = useState(false);
  const [expandCmd, setExpandCmd] = useState({ version: 0, expand: true });

  const parsed = useMemo(() => {
    if (!raw) return { ok: false as const, value: undefined as unknown };
    try {
      return { ok: true as const, value: JSON.parse(raw) as unknown };
    } catch {
      return { ok: false as const, value: undefined as unknown };
    }
  }, [raw]);

  const text = useMemo(() => {
    if (!raw) return "(无)";
    return parsed.ok ? JSON.stringify(parsed.value, null, 2) : raw;
  }, [raw, parsed]);

  const effectiveMode: ViewMode = mode === "tree" && parsed.ok ? "tree" : "text";

  const highlighted = useMemo(
    () => (effectiveMode === "text" ? highlightJson(text) : null),
    [text, effectiveMode],
  );

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch { /* ignore */ }
  };

  const format = () => {
    if (parsed.ok) {
      toast.success("已格式化");
    } else {
      toast.error("内容不是有效的 JSON，无法格式化");
    }
  };

  return (
    <div className="overflow-hidden rounded-md border border-black/10 bg-[#f6f8fa] dark:border-white/10 dark:bg-[#0d1117]">
      <div className="flex items-center justify-between gap-2 border-b border-black/10 bg-[#eaeef2] px-2 py-1 dark:border-white/10 dark:bg-[#161b22]">
        <div className="flex items-center gap-0.5 rounded-md border border-black/10 bg-black/5 p-0.5 dark:border-white/10 dark:bg-white/5">
          <ModeButton active={effectiveMode === "text"} onClick={() => setMode("text")}>文本</ModeButton>
          <ModeButton active={effectiveMode === "tree"} disabled={!parsed.ok} onClick={() => setMode("tree")}>树形</ModeButton>
        </div>
        <div className="flex items-center gap-1 text-[11px] text-muted-foreground">
          {effectiveMode === "tree" ? (
            <>
              <ToolButton onClick={() => setExpandCmd((s) => ({ version: s.version + 1, expand: true }))}>
                <ChevronsUpDown className="size-3" />展开
              </ToolButton>
              <ToolButton onClick={() => setExpandCmd((s) => ({ version: s.version + 1, expand: false }))}>
                <ChevronsDownUp className="size-3" />折叠
              </ToolButton>
            </>
          ) : (
            <ToolButton onClick={format}>
              <Braces className="size-3" />格式化
            </ToolButton>
          )}
          <ToolButton onClick={copy}>
            {copied ? <><Check className="size-3 text-green-500" />已复制</> : <><Copy className="size-3" />复制</>}
          </ToolButton>
        </div>
      </div>
      <div className={cn("overflow-auto", maxHeightClass)}>
        {effectiveMode === "text" ? (
          <pre className="hljs whitespace-pre p-3 text-xs leading-relaxed">{highlighted}</pre>
        ) : (
          <div className="p-2">
            <JsonTree
              key={expandCmd.version}
              data={parsed.value}
              defaultDepth={expandCmd.version === 0 ? 2 : expandCmd.expand ? Number.MAX_SAFE_INTEGER : 0}
            />
          </div>
        )}
      </div>
    </div>
  );
}
