import { useEffect, useRef, useState } from "react";
import { echo } from "@/services/health";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

const HIGH_LATENCY_MS = 10000;
const POLL_INTERVAL_MS = 10000;

type Status = "online" | "high-latency" | "offline";

export function ServiceStatusIndicator() {
  const [status, setStatus] = useState<Status>("online");
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let aborted = false;

    const check = async () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
        timeoutRef.current = null;
      }
      const start = performance.now();
      try {
        await echo();
        const elapsed = performance.now() - start;
        if (!aborted) {
          setStatus(elapsed > HIGH_LATENCY_MS ? "high-latency" : "online");
        }
      } catch {
        if (!aborted) {
          setStatus("offline");
        }
      } finally {
        if (!aborted) {
          timeoutRef.current = setTimeout(check, POLL_INTERVAL_MS);
        }
      }
    };

    check();

    return () => {
      aborted = true;
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, []);

  const config = {
    online: { label: "在线", className: "bg-green-500" },
    "high-latency": { label: "高延迟", className: "bg-yellow-500" },
    offline: { label: "离线", className: "bg-red-500" },
  }[status];

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div className="flex cursor-pointer items-center gap-1.5 rounded-md px-2 py-1 hover:bg-muted">
          <span className={`size-2.5 rounded-full ${config.className}`} />
          <span className="text-xs text-muted-foreground">{config.label}</span>
        </div>
      </TooltipTrigger>
      <TooltipContent side="bottom">
        服务状态：{config.label}
      </TooltipContent>
    </Tooltip>
  );
}
