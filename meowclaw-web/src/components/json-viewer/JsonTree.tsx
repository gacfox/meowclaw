import { Fragment, useState } from "react";
import { ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";

function Primitive({ value }: { value: unknown }) {
  if (value === null) return <span className="hljs-literal">null</span>;
  switch (typeof value) {
    case "string":
      return <span className="hljs-string">&quot;{value}&quot;</span>;
    case "number":
      return <span className="hljs-number">{String(value)}</span>;
    case "boolean":
      return <span className="hljs-literal">{String(value)}</span>;
    default:
      return <span>{String(value)}</span>;
  }
}

function KeyLabel({ name }: { name: string }) {
  return (
    <>
      <span className="hljs-attr">&quot;{name}&quot;</span>
      <span className="text-muted-foreground">:</span>
    </>
  );
}

interface TreeNodeProps {
  keyName?: string;
  value: unknown;
  depth: number;
  defaultDepth: number;
}

function TreeNode({ keyName, value, depth, defaultDepth }: TreeNodeProps) {
  const isContainer = typeof value === "object" && value !== null;
  const [expanded, setExpanded] = useState(depth < defaultDepth);

  if (!isContainer) {
    return (
      <div className="flex min-w-0 items-baseline gap-1 rounded px-1 py-px hover:bg-black/5 dark:hover:bg-white/10">
        <span className="inline-block w-3.5 shrink-0" />
        {keyName !== undefined && <KeyLabel name={keyName} />}
        <Primitive value={value} />
      </div>
    );
  }

  const entries: [string, unknown][] = Array.isArray(value)
    ? value.map((item, i) => [String(i), item])
    : Object.entries(value);
  const open = Array.isArray(value) ? "[" : "{";
  const close = Array.isArray(value) ? "]" : "}";

  if (entries.length === 0) {
    return (
      <div className="flex items-baseline gap-1 rounded px-1 py-px hover:bg-black/5 dark:hover:bg-white/10">
        <span className="inline-block w-3.5 shrink-0" />
        {keyName !== undefined && <KeyLabel name={keyName} />}
        <span className="text-muted-foreground">{open} {close}</span>
      </div>
    );
  }

  return (
    <div>
      <div
        className="flex cursor-pointer select-none items-center gap-1 rounded px-1 py-px hover:bg-black/5 dark:hover:bg-white/10"
        onClick={() => setExpanded((prev) => !prev)}
      >
        <ChevronRight
          className={cn("size-3.5 shrink-0 text-muted-foreground transition-transform", expanded && "rotate-90")}
        />
        {keyName !== undefined && <KeyLabel name={keyName} />}
        <span className="text-muted-foreground">{open}</span>
        {!expanded && (
          <span className="ml-1 rounded bg-black/5 px-1.5 py-px text-[10px] leading-4 text-muted-foreground dark:bg-white/10">
            {entries.length} 项 {close}
          </span>
        )}
      </div>
      {expanded && (
        <Fragment>
          <div className="ml-3 border-l border-black/10 pl-2 dark:border-white/10">
            {entries.map(([k, v]) => (
              <TreeNode
                key={k}
                keyName={k}
                value={v}
                depth={depth + 1}
                defaultDepth={defaultDepth}
              />
            ))}
          </div>
          <div className="px-1 text-muted-foreground">{close}</div>
        </Fragment>
      )}
    </div>
  );
}

interface JsonTreeProps {
  data: unknown;
  defaultDepth?: number;
}

export function JsonTree({ data, defaultDepth = 2 }: JsonTreeProps) {
  return (
    <div className="font-mono text-xs leading-5">
      <TreeNode value={data} depth={0} defaultDepth={defaultDepth} />
    </div>
  );
}
