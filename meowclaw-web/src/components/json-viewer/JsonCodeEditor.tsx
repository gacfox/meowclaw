import { useMemo, useRef } from "react";
import { highlightJson } from "./highlight-json";

/**
 * 带语法高亮的JSON编辑区：透明文字的textarea叠在高亮层上方，滚动实时同步
 */
export function JsonCodeEditor({
  value,
  onChange,
  readOnly,
}: {
  value: string;
  onChange?: (value: string) => void;
  readOnly?: boolean;
}) {
  const preRef = useRef<HTMLPreElement>(null);
  const highlighted = useMemo(() => highlightJson(value), [value]);

  const syncScroll = (e: React.UIEvent<HTMLTextAreaElement>) => {
    const target = e.currentTarget;
    if (preRef.current) {
      preRef.current.style.transform = `translate(${-target.scrollLeft}px, ${-target.scrollTop}px)`;
    }
  };

  return (
    <div className="relative min-h-96 overflow-hidden rounded-md border border-input bg-[#f6f8fa] transition-shadow focus-within:border-ring focus-within:ring-[3px] focus-within:ring-ring/50 dark:border-white/10 dark:bg-[#0d1117]">
      <pre
        ref={preRef}
        aria-hidden
        className="hljs pointer-events-none absolute inset-0 w-max min-w-full overflow-hidden whitespace-pre p-3 font-mono text-xs leading-5"
      >
        {highlighted}
        {"\n"}
      </pre>
      <textarea
        value={value}
        readOnly={readOnly}
        onChange={(e) => onChange?.(e.target.value)}
        onScroll={syncScroll}
        spellCheck={false}
        wrap="off"
        className="relative min-h-96 w-full resize-none overflow-auto whitespace-pre bg-transparent p-3 font-mono text-xs leading-5 text-transparent caret-black outline-none selection:bg-blue-500/30 dark:caret-white"
      />
    </div>
  );
}
