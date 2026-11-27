import type { LlmCallLogItem } from "@/types";
import { JsonViewer } from "@/components/json-viewer/JsonViewer";
import { Badge } from "@/components/ui/badge";
import { ArrowDown, ArrowUp, ChevronRight, Database, Zap } from "lucide-react";
import { formatTraceTimestamp } from "./traceUtils";

const PURPOSE_LABELS: Record<string, string> = {
  agent: "主循环",
  title: "标题",
  recap: "摘要",
  memory: "记忆",
};

export function LlmCallCard({ call }: { call: LlmCallLogItem }) {
  const outputTokensPerSecond = call.status === "SUCCESS" && call.durationMs != null
    && call.durationMs > 0 && call.outputTokens > 0
    ? call.outputTokens / (call.durationMs / 1000)
    : null;
  return (
    <details className="group rounded-lg border text-sm">
      <summary className="flex cursor-pointer list-none flex-wrap items-center gap-2 p-3 [&::-webkit-details-marker]:hidden">
        <ChevronRight className="size-3.5 shrink-0 transition-transform group-open:rotate-90" />
        <Badge variant="outline">{PURPOSE_LABELS[call.purpose ?? ""] ?? call.purpose ?? "未知"}</Badge>
        <span className="font-mono text-xs">{call.model}</span>
        {call.status === "ERROR"
          ? <Badge variant="destructive">失败</Badge>
          : <Badge variant="secondary">成功</Badge>}
        <span className="ml-auto flex flex-wrap items-center justify-end gap-2 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-0.5"><ArrowUp className="size-3" />{call.inputTokens}</span>
          <span className="inline-flex items-center gap-0.5"><ArrowDown className="size-3" />{call.outputTokens}</span>
          {call.cachedTokens != null && call.cachedTokens > 0 && (
            <span title="缓存输入 Tokens" className="inline-flex items-center gap-0.5">
              <Database className="size-3" />
              {call.cachedTokens}
            </span>
          )}
          {outputTokensPerSecond != null && (
            <span title="平均输出速度" className="inline-flex items-center gap-0.5">
              <Zap className="size-3" />
              {outputTokensPerSecond.toFixed(1)} tok/s
            </span>
          )}
          {call.durationMs != null && <span>{(call.durationMs / 1000).toFixed(1)}s</span>}
          <span>{formatTraceTimestamp(call.createdAt)}</span>
        </span>
      </summary>
      <div className="flex flex-col gap-3 border-t p-3">
        {call.errorMessage && (
          <div className="rounded bg-destructive/10 p-2 text-xs text-destructive">{call.errorMessage}</div>
        )}
        {call.reasoningContent && (
          <details className="text-xs">
            <summary className="cursor-pointer text-muted-foreground">推理内容</summary>
            <pre className="mt-1 max-h-64 overflow-auto whitespace-pre-wrap rounded bg-muted p-2">{call.reasoningContent}</pre>
          </details>
        )}
        <div className="text-xs">
          <div className="mb-1 text-muted-foreground">请求消息</div>
          <JsonViewer raw={call.requestMessages} maxHeightClass="max-h-96" />
        </div>
        {call.responseContent != null && call.responseContent.trim() !== "" && (
          <div className="text-xs">
            <div className="mb-1 text-muted-foreground">响应内容</div>
            <pre className="mt-1 max-h-64 overflow-auto whitespace-pre-wrap rounded bg-muted p-2">{call.responseContent}</pre>
          </div>
        )}
        {call.responseToolCalls && (
          <div className="text-xs">
            <div className="mb-1 text-muted-foreground">响应工具调用</div>
            <JsonViewer raw={call.responseToolCalls} maxHeightClass="max-h-64" />
          </div>
        )}
      </div>
    </details>
  );
}
