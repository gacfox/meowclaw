import { lazy, Suspense } from "react";
import type { FileContent, FileEntry } from "@/types";
import { extensionOf } from "@/lib/workspaceFiles";
import { MarkdownRenderer } from "@/components/markdown/MarkdownRenderer";
import { WorkspaceImagePreview } from "./WorkspaceImagePreview";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

const WorkspacePdfPreview = lazy(() => import("@/components/workspace/WorkspacePdfPreview")
  .then((module) => ({ default: module.WorkspacePdfPreview })));
const MD_EXTS = new Set(["md", "markdown"]);
const EXT_TO_LANG: Record<string, string> = {
  ts: "typescript", tsx: "typescript", mjs: "javascript", cjs: "javascript", js: "javascript", jsx: "javascript",
  java: "java", kt: "kotlin", py: "python", go: "go", rs: "rust", c: "c", h: "c", cpp: "cpp", cc: "cpp", hpp: "hpp",
  cs: "csharp", rb: "ruby", php: "php", swift: "swift",
  css: "css", scss: "scss", less: "less", html: "html", htm: "html", xml: "xml",
  json: "json", yml: "yaml", yaml: "yaml", toml: "toml", ini: "ini", properties: "properties",
  sh: "bash", bash: "bash", zsh: "bash", bat: "bat", ps1: "powershell",
  sql: "sql", graphql: "graphql", gql: "graphql", vue: "vue", svelte: "svelte",
};

function codeFence(name: string, content: string): string {
  const lang = EXT_TO_LANG[extensionOf(name)] ?? "";
  return (lang ? "```" + lang + "\n" : "```\n") + content + "\n```";
}

export type WorkspacePreviewMode = "view" | "edit";

interface WorkspacePreviewDialogProps {
  entry: FileEntry | null;
  content: FileContent | null;
  pdfUrl: string | null;
  loading: boolean;
  error: string | null;
  mode: WorkspacePreviewMode;
  editText: string;
  editModified: boolean;
  editSaving: boolean;
  downloading: boolean;
  onClose: () => void;
  onDownload: (entry: FileEntry) => void;
  onModeChange: (mode: WorkspacePreviewMode) => void;
  onEditTextChange: (value: string) => void;
  onSave: () => void;
}

export function WorkspacePreviewDialog({
  entry,
  content,
  pdfUrl,
  loading,
  error,
  mode,
  editText,
  editModified,
  editSaving,
  downloading,
  onClose,
  onDownload,
  onModeChange,
  onEditTextChange,
  onSave,
}: WorkspacePreviewDialogProps) {
  return (
    <Dialog open={!!entry} onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="flex max-h-[90vh] flex-col overflow-hidden sm:max-w-6xl">
        <DialogHeader><DialogTitle className="break-all">{entry?.path}</DialogTitle></DialogHeader>
        {loading ? (
          <div className="py-8 text-center text-muted-foreground">加载中...</div>
        ) : error ? (
          <div className="py-8 text-center text-destructive">{error}</div>
        ) : pdfUrl && entry ? (
          <Suspense fallback={<div className="py-8 text-center text-muted-foreground">正在加载 PDF 查看器...</div>}>
            <WorkspacePdfPreview
              key={pdfUrl}
              url={pdfUrl}
              downloading={downloading}
              onDownload={() => onDownload(entry)}
            />
          </Suspense>
        ) : content?.kind === "IMAGE" ? (
          <WorkspaceImagePreview blob={content.blob} name={entry?.name ?? "图片预览"} />
        ) : content?.kind === "TEXT" ? (
          <>
            <div className="min-h-0 flex-1 overflow-auto">
              {mode === "view" ? (
                <MarkdownRenderer content={MD_EXTS.has(extensionOf(entry?.name ?? ""))
                  ? content.content ?? ""
                  : codeFence(entry?.name ?? "", content.content ?? "")} />
              ) : (
                <Textarea
                  rows={20}
                  className="resize-y font-mono text-sm"
                  value={editText}
                  onChange={(event) => onEditTextChange(event.target.value)}
                />
              )}
            </div>
            <DialogFooter>
              {mode === "view" ? (
                <>
                  <Button variant="outline" onClick={onClose}>关闭</Button>
                  <Button onClick={() => onModeChange("edit")}>编辑</Button>
                </>
              ) : (
                <>
                  <Button variant="outline" onClick={() => onModeChange("view")}>预览</Button>
                  <Button onClick={onSave} disabled={editSaving || !editModified}>
                    {editSaving ? "保存中..." : "保存"}
                  </Button>
                </>
              )}
            </DialogFooter>
          </>
        ) : (
          <div className="py-8 text-center text-muted-foreground">该文件类型不支持在线预览</div>
        )}
      </DialogContent>
    </Dialog>
  );
}
