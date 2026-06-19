import { useEffect, useId, useState } from "react";
import { useTheme } from "next-themes";
import { Loader2 } from "lucide-react";

type Mermaid = typeof import("mermaid")["default"];
let mermaidCache: Promise<Mermaid> | null = null;
function loadMermaid(): Promise<Mermaid> {
  if (!mermaidCache) mermaidCache = import("mermaid").then((m) => m.default);
  return mermaidCache;
}

export function MermaidBlock({ chart }: { chart: string }) {
  const rawId = useId();
  const id = "mmd-" + rawId.replace(/[^a-zA-Z0-9]/g, "");
  const { resolvedTheme } = useTheme();
  const [svg, setSvg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setSvg(null);
    setError(null);
    (async () => {
      try {
        const mermaid = await loadMermaid();
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: "loose",
          theme: resolvedTheme === "dark" ? "dark" : "default",
        });
        const result = await mermaid.render(id, chart);
        if (!cancelled) setSvg(result.svg);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      }
    })();
    return () => { cancelled = true; };
  }, [chart, id, resolvedTheme]);

  if (error) {
    return (
      <div className="not-prose my-3 rounded-md border border-destructive/50 p-2 text-xs">
        <div className="mb-1 text-destructive">Mermaid 渲染失败：{error}</div>
        <pre className="whitespace-pre-wrap text-muted-foreground">{chart}</pre>
      </div>
    );
  }
  if (!svg) {
    return (
      <div className="not-prose my-3 flex items-center gap-2 text-xs text-muted-foreground">
        <Loader2 className="size-3 animate-spin" /> 渲染图表中...
      </div>
    );
  }
  return <div className="not-prose my-3 overflow-x-auto" dangerouslySetInnerHTML={{ __html: svg }} />;
}
