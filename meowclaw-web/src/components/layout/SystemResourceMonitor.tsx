import { useEffect, useRef, useState } from "react";
import { Gauge } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useIsMobile } from "@/hooks/use-mobile";
import { cn } from "@/lib/utils";
import {
  getSystemResourceMetrics,
  getSystemResourceMonitorEnabled,
  SYSTEM_RESOURCE_MONITOR_CHANGED_EVENT,
  type SystemResourceMetrics,
} from "@/services/systemResource";

const POLL_INTERVAL_MS = 10000;

function formatUsage(value: number | null): string {
  return value === null ? "--" : `${value.toFixed(1)}%`;
}

function ResourceProgress({
  label,
  value,
  className,
  barClassName,
}: {
  label: string;
  value: number | null;
  className?: string;
  barClassName?: string;
}) {
  const normalizedValue = value === null ? null : Math.min(Math.max(value, 0), 100);
  const description = `${label} 使用率：${formatUsage(normalizedValue)}`;

  return (
    <div className={cn("flex items-center gap-2 text-xs", className)} title={description}>
      <span className="w-8 shrink-0 text-muted-foreground">{label}</span>
      <div
        className={cn("h-1.5 overflow-hidden rounded-full bg-muted", barClassName)}
        role="progressbar"
        aria-label={`${label}使用率`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={normalizedValue ?? undefined}
        aria-valuetext={formatUsage(normalizedValue)}
      >
        <div
          className="h-full rounded-full bg-primary transition-[width] duration-300"
          style={{ width: `${normalizedValue ?? 0}%` }}
        />
      </div>
      <span className="w-11 shrink-0 text-right font-mono text-[11px] text-muted-foreground">
        {formatUsage(normalizedValue)}
      </span>
    </div>
  );
}

export function SystemResourceMonitor() {
  const isMobile = useIsMobile();
  const [enabled, setEnabled] = useState(false);
  const [metrics, setMetrics] = useState<SystemResourceMetrics | null>(null);
  const [popoverOpen, setPopoverOpen] = useState(false);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let active = true;

    const loadEnabled = async () => {
      try {
        const value = await getSystemResourceMonitorEnabled();
        if (active) setEnabled(value);
      } catch {
        // 配置不可用时隐藏小组件，不影响现有服务状态指示器
      }
    };

    const handleEnabledChange = (event: Event) => {
      const value = (event as CustomEvent<boolean>).detail;
      setEnabled(value);
      setMetrics(null);
      if (!value) setPopoverOpen(false);
    };

    void loadEnabled();
    window.addEventListener(SYSTEM_RESOURCE_MONITOR_CHANGED_EVENT, handleEnabledChange);
    return () => {
      active = false;
      window.removeEventListener(SYSTEM_RESOURCE_MONITOR_CHANGED_EVENT, handleEnabledChange);
    };
  }, []);

  useEffect(() => {
    if (!enabled || (isMobile && !popoverOpen)) return;

    let aborted = false;

    const poll = async () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
        timeoutRef.current = null;
      }
      try {
        const value = await getSystemResourceMetrics();
        if (!aborted) setMetrics(value);
      } catch {
        if (!aborted) setMetrics(null);
      } finally {
        if (!aborted) {
          timeoutRef.current = setTimeout(poll, POLL_INTERVAL_MS);
        }
      }
    };

    void poll();
    return () => {
      aborted = true;
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
        timeoutRef.current = null;
      }
    };
  }, [enabled, isMobile, popoverOpen]);

  if (!enabled) return null;

  const cpuUsage = metrics?.cpuUsage ?? null;
  const memoryUsage = metrics?.memoryUsage ?? null;

  return (
    <>
      <div className="hidden items-center gap-3 md:flex" aria-label="系统资源占用">
        <ResourceProgress label="CPU" value={cpuUsage} barClassName="w-14" />
        <ResourceProgress label="内存" value={memoryUsage} barClassName="w-14" />
      </div>

      <div className="md:hidden">
        <Popover open={popoverOpen} onOpenChange={setPopoverOpen}>
          <PopoverTrigger asChild>
            <Button variant="ghost" size="icon-sm" aria-label="查看系统资源占用">
              <Gauge className="size-4" />
            </Button>
          </PopoverTrigger>
          <PopoverContent align="end" className="w-64 gap-4 p-4">
            <div className="flex items-center gap-2 text-sm font-medium">
              <Gauge className="size-4 text-muted-foreground" />
              系统资源占用
            </div>
            <div className="flex flex-col gap-3">
              <ResourceProgress label="CPU" value={cpuUsage} barClassName="min-w-0 flex-1" />
              <ResourceProgress label="内存" value={memoryUsage} barClassName="min-w-0 flex-1" />
            </div>
          </PopoverContent>
        </Popover>
      </div>
    </>
  );
}
