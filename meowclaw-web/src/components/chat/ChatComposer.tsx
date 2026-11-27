import { useRef, type RefObject } from "react";
import { toast } from "sonner";
import { ArrowUp, Brain, Loader2, Plus, Shield, Square, X } from "lucide-react";
import type { GuardrailPolicyDTO } from "@/types";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { ContextUsageGauge } from "./ContextUsageGauge";

export interface ChatPendingImage {
  dataUrl: string;
  name: string;
}

interface ChatComposerProps {
  pendingImages: ChatPendingImage[];
  input: string;
  canVision: boolean;
  isRunning: boolean;
  stopping: boolean;
  reasoningEffort: string | null;
  effortOptions: string[];
  policies: GuardrailPolicyDTO[];
  currentGuardrailPolicyId: number | null | undefined;
  showContextUsage: boolean;
  contextPromptTokens: number;
  contextLength: number;
  textareaRef: RefObject<HTMLTextAreaElement | null>;
  onInputChange: (value: string) => void;
  onRemoveImage: (index: number) => void;
  onFilesSelected: (files: FileList | File[] | null) => void;
  onSend: () => void;
  onStop: () => void;
  onReasoningEffortChange: (value: string) => void;
  onPolicyChange: (value: string) => void;
}

export function ChatComposer({
  pendingImages,
  input,
  canVision,
  isRunning,
  stopping,
  reasoningEffort,
  effortOptions,
  policies,
  currentGuardrailPolicyId,
  showContextUsage,
  contextPromptTokens,
  contextLength,
  textareaRef,
  onInputChange,
  onRemoveImage,
  onFilesSelected,
  onSend,
  onStop,
  onReasoningEffortChange,
  onPolicyChange,
}: ChatComposerProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  return (
    <div className="rounded-2xl border border-input bg-background px-3 pb-2 pt-3 shadow-sm transition-shadow focus-within:border-ring focus-within:ring-[3px] focus-within:ring-ring/50">
      {pendingImages.length > 0 && (
        <div className="mb-2 flex flex-wrap gap-2">
          {pendingImages.map((image, index) => (
            <div key={index} className="relative">
              <img src={image.dataUrl} alt={image.name} className="size-14 rounded border object-cover" />
              <button
                className="absolute -right-1.5 -top-1.5 rounded-full bg-destructive p-0.5 text-destructive-foreground"
                onClick={() => onRemoveImage(index)}
                title="移除"
              >
                <X className="size-3" />
              </button>
            </div>
          ))}
        </div>
      )}
      <textarea
        ref={textareaRef}
        value={input}
        onChange={(event) => onInputChange(event.target.value)}
        onPaste={(event) => {
          const images = Array.from(event.clipboardData.files).filter((file) =>
            ["image/png", "image/jpeg", "image/webp", "image/gif"].includes(file.type)
          );
          if (images.length === 0) return;
          event.preventDefault();
          if (!canVision) {
            toast.error("当前模型不支持图片输入");
            return;
          }
          onFilesSelected(images);
        }}
        onKeyDown={(event) => {
          if (event.key === "Enter" && !event.shiftKey && !event.ctrlKey && !event.nativeEvent.isComposing) {
            event.preventDefault();
            onSend();
          }
        }}
        placeholder={isRunning ? "正在执行中..." : "输入消息..."}
        rows={1}
        autoFocus
        className="w-full resize-none bg-transparent px-1 py-1 text-sm outline-none placeholder:text-muted-foreground"
      />
      <div className="mt-1 flex items-center justify-between gap-2">
        <input
          ref={fileInputRef}
          type="file"
          accept="image/png,image/jpeg,image/webp,image/gif"
          multiple
          className="hidden"
          onChange={(event) => { onFilesSelected(event.target.files); event.target.value = ""; }}
        />
        <div className="flex min-w-0 flex-wrap items-center gap-1">
          <Tooltip>
            <TooltipTrigger asChild>
              <span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="rounded-full text-muted-foreground"
                  disabled={!canVision}
                  onClick={() => fileInputRef.current?.click()}
                >
                  <Plus className="size-4" />
                </Button>
              </span>
            </TooltipTrigger>
            <TooltipContent>{canVision ? "上传图片" : "当前模型不支持图片输入"}</TooltipContent>
          </Tooltip>
          {reasoningEffort && (
            <Select value={reasoningEffort} onValueChange={onReasoningEffortChange}>
              <SelectTrigger className="h-7 w-auto max-w-32 gap-1 rounded-full border-none bg-transparent px-2 text-xs text-muted-foreground shadow-none focus:ring-0 sm:max-w-none" title="思考强度">
                <Brain className="size-3.5" />
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {effortOptions.map((effort) => (
                  <SelectItem key={effort} value={effort}>{effort}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
          <Select
            value={currentGuardrailPolicyId != null ? String(currentGuardrailPolicyId) : undefined}
            onValueChange={onPolicyChange}
          >
            <SelectTrigger className="h-7 w-auto max-w-32 gap-1 rounded-full border-none bg-transparent px-2 text-xs text-muted-foreground shadow-none focus:ring-0 sm:max-w-none">
              <Shield className="size-3.5" />
              <SelectValue placeholder="护栏策略" />
            </SelectTrigger>
            <SelectContent>
              {policies.map((policy) => (
                <SelectItem key={policy.id} value={String(policy.id)}>{policy.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          {showContextUsage && (
            <ContextUsageGauge promptTokens={contextPromptTokens} contextLength={contextLength} />
          )}
        </div>
        {isRunning ? (
          <Button variant="destructive" size="icon" className="rounded-full" onClick={onStop} disabled={stopping} title="停止执行">
            {stopping ? <Loader2 className="size-4 animate-spin" /> : <Square className="size-3.5" fill="currentColor" />}
          </Button>
        ) : (
          <Button size="icon" className="rounded-full" onClick={onSend} disabled={!input.trim() && pendingImages.length === 0} title="发送">
            <ArrowUp className="size-4" />
          </Button>
        )}
      </div>
    </div>
  );
}
