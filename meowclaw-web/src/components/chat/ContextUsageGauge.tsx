function formatTokenCount(value: number): string {
  const format = (num: number, unit: string) => `${num.toFixed(1).replace(/\.0$/, "")}${unit}`;
  if (value >= 1_000_000) return format(value / 1_000_000, "M");
  if (value >= 1_000) return format(value / 1_000, "K");
  return String(value);
}

export function ContextUsageGauge({
  promptTokens,
  contextLength,
}: {
  promptTokens: number;
  contextLength: number;
}) {
  const ratio = promptTokens / contextLength;
  const clampedRatio = Math.min(Math.max(ratio, 0), 1);
  const percentage = (ratio * 100).toFixed(1);
  const radius = 8;
  const circumference = 2 * Math.PI * radius;
  const colorClass = ratio >= 0.93 ? "text-destructive" : ratio >= 0.82 ? "text-amber-500" : "text-primary";
  const description = `${percentage}% · ${formatTokenCount(promptTokens)} / ${formatTokenCount(contextLength)} 上下文已占用`;

  return (
    <div className="flex items-center gap-1.5 whitespace-nowrap text-xs text-muted-foreground" title={description}>
      <svg
        viewBox="0 0 20 20"
        className={`size-4 shrink-0 ${colorClass}`}
        role="img"
        aria-label={description}
      >
        <circle
          cx="10"
          cy="10"
          r={radius}
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          className="text-muted-foreground/20"
        />
        <circle
          cx="10"
          cy="10"
          r={radius}
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - clampedRatio)}
          transform="rotate(-90 10 10)"
        />
      </svg>
      <span>{description}</span>
    </div>
  );
}
