import { useEffect, useMemo, useRef, useState } from "react";
import {
  ChevronLeft,
  ChevronRight,
  Download,
  Loader2,
  Maximize,
  RotateCw,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import { Document, Page, PasswordResponses, pdfjs } from "react-pdf";
import type { PDFPageProxy } from "pdfjs-dist";
import "react-pdf/dist/Page/AnnotationLayer.css";
import "react-pdf/dist/Page/TextLayer.css";
import { getToken } from "@/services/request";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  "pdfjs-dist/build/pdf.worker.min.mjs",
  import.meta.url,
).toString();

const MIN_SCALE = 0.25;
const MAX_SCALE = 4;
const SCALE_STEP = 0.25;

interface PasswordPrompt {
  callback: (password: string | null) => void;
  reason: number;
}

interface WorkspacePdfPreviewProps {
  url: string;
  downloading?: boolean;
  onDownload: () => void;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "PDF 文件加载失败";
}

export function WorkspacePdfPreview({ url, downloading = false, onDownload }: WorkspacePdfPreviewProps) {
  const [numPages, setNumPages] = useState(0);
  const [pageNumber, setPageNumber] = useState(1);
  const [scale, setScale] = useState<number | null>(null);
  const [rotation, setRotation] = useState<number | null>(null);
  const [containerWidth, setContainerWidth] = useState(0);
  const [currentPage, setCurrentPage] = useState<PDFPageProxy | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [progress, setProgress] = useState<{ loaded: number; total: number } | null>(null);
  const [passwordPrompt, setPasswordPrompt] = useState<PasswordPrompt | null>(null);
  const [password, setPassword] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);

  const file = useMemo(() => ({ url }), [url]);
  const options = useMemo(() => {
    const token = getToken();
    return {
      cMapUrl: "/pdfjs/cmaps/",
      cMapPacked: true,
      standardFontDataUrl: "/pdfjs/standard_fonts/",
      wasmUrl: "/pdfjs/wasm/",
      iccUrl: "/pdfjs/iccs/",
      disableAutoFetch: true,
      disableStream: true,
      httpHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    };
  }, []);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const observer = new ResizeObserver(([entry]) => {
      setContainerWidth(entry.contentRect.width);
    });
    observer.observe(container);
    return () => observer.disconnect();
  }, []);

  const availableWidth = Math.max(1, containerWidth - 24);
  const effectiveRotation = rotation ?? currentPage?.rotate ?? 0;
  const fitPageWidth = currentPage?.getViewport({ scale: 1, rotation: effectiveRotation }).width ?? 0;
  const fitScale = fitPageWidth > 0 ? availableWidth / fitPageWidth : 1;
  const displayedScale = scale ?? fitScale;
  const progressPercent = progress?.total
    ? Math.min(100, Math.round(progress.loaded / progress.total * 100))
    : null;

  const changeScale = (direction: number) => {
    const next = displayedScale + direction * SCALE_STEP;
    setScale(Math.min(MAX_SCALE, Math.max(MIN_SCALE, next)));
  };

  const submitPage = (value: string): number => {
    if (!numPages) return pageNumber;
    const parsed = Number.parseInt(value, 10);
    const next = Number.isFinite(parsed) ? Math.min(numPages, Math.max(1, parsed)) : pageNumber;
    setPageNumber(next);
    return next;
  };

  const loading = (
    <div className="flex min-h-48 flex-col items-center justify-center gap-2 text-muted-foreground">
      <Loader2 className="size-5 animate-spin" />
      <span>正在加载 PDF{progressPercent === null ? "..." : `（${progressPercent}%）`}</span>
    </div>
  );

  return (
    <div className="relative flex h-[72dvh] min-h-0 flex-col overflow-hidden rounded-lg border bg-background">
      <div className="flex shrink-0 flex-wrap items-center justify-center gap-1 border-b bg-muted/30 p-1.5">
        <Button
          variant="ghost"
          size="icon"
          title="上一页"
          aria-label="上一页"
          disabled={pageNumber <= 1 || !numPages}
          onClick={() => setPageNumber((current) => Math.max(1, current - 1))}
        ><ChevronLeft /></Button>
        <div className="flex items-center gap-1 text-sm whitespace-nowrap">
          <Input
            key={`${pageNumber}-${numPages}`}
            className="h-8 w-14 px-1 text-center"
            type="number"
            min={1}
            max={numPages || 1}
            defaultValue={pageNumber}
            disabled={!numPages}
            aria-label="页码"
            onBlur={(event) => { event.currentTarget.value = String(submitPage(event.currentTarget.value)); }}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.currentTarget.value = String(submitPage(event.currentTarget.value));
                event.currentTarget.blur();
              }
            }}
          />
          <span className="text-muted-foreground">/ {numPages || "-"}</span>
        </div>
        <Button
          variant="ghost"
          size="icon"
          title="下一页"
          aria-label="下一页"
          disabled={!numPages || pageNumber >= numPages}
          onClick={() => setPageNumber((current) => Math.min(numPages, current + 1))}
        ><ChevronRight /></Button>
        <div className="mx-1 hidden h-5 w-px bg-border sm:block" />
        <Button variant="ghost" size="icon" title="缩小" aria-label="缩小" disabled={displayedScale <= MIN_SCALE} onClick={() => changeScale(-1)}><ZoomOut /></Button>
        <span className="w-12 text-center text-xs text-muted-foreground">{Math.round(displayedScale * 100)}%</span>
        <Button variant="ghost" size="icon" title="放大" aria-label="放大" disabled={displayedScale >= MAX_SCALE} onClick={() => changeScale(1)}><ZoomIn /></Button>
        <Button variant={scale === null ? "secondary" : "ghost"} size="icon" title="适应宽度" aria-label="适应宽度" onClick={() => setScale(null)}><Maximize /></Button>
        <Button
          variant="ghost"
          size="icon"
          title="顺时针旋转"
          aria-label="顺时针旋转"
          onClick={() => setRotation((current) => ((current ?? currentPage?.rotate ?? 0) + 90) % 360)}
        ><RotateCw /></Button>
        <Button variant="ghost" size="icon" title="下载 PDF" aria-label="下载 PDF" disabled={downloading} onClick={onDownload}>
          {downloading ? <Loader2 className="animate-spin" /> : <Download />}
        </Button>
      </div>

      <div ref={containerRef} className="min-h-0 flex-1 overflow-auto bg-muted/30 p-3">
        <Document
          file={file}
          options={options}
          suspense={false}
          loading={loading}
          error={<div className="p-8 text-center text-destructive">PDF 加载失败：{loadError ?? "无法读取文件"}</div>}
          externalLinkTarget="_blank"
          externalLinkRel="noopener noreferrer nofollow"
          onLoadProgress={({ loaded, total }) => setProgress({ loaded, total })}
          onLoadSuccess={(pdf) => {
            setNumPages(pdf.numPages);
            setPageNumber((current) => Math.min(pdf.numPages, Math.max(1, current)));
            setLoadError(null);
          }}
          onLoadError={(error) => setLoadError(errorMessage(error))}
          onSourceError={(error) => setLoadError(errorMessage(error))}
          onPassword={(callback, reason) => {
            setPassword("");
            setPasswordPrompt({ callback, reason });
          }}
        >
          {numPages > 0 && (
            <div className="flex min-h-full items-start justify-center">
              <Page
                key={`${pageNumber}-${rotation ?? "auto"}`}
                pageNumber={pageNumber}
                rotate={rotation ?? undefined}
                width={scale === null && containerWidth > 0 ? availableWidth : undefined}
                scale={scale ?? 1}
                className="bg-white shadow-md"
                loading={loading}
                error={<div className="p-8 text-center text-destructive">当前页面渲染失败</div>}
                onLoadSuccess={setCurrentPage}
              />
            </div>
          )}
        </Document>
      </div>

      {passwordPrompt && (
        <div className="absolute inset-0 z-20 flex items-center justify-center bg-background/95 p-6">
          <form
            className="flex w-full max-w-sm flex-col gap-3"
            onSubmit={(event) => {
              event.preventDefault();
              passwordPrompt.callback(password);
              setPasswordPrompt(null);
            }}
          >
            <div>
              <h3 className="font-medium">此 PDF 受密码保护</h3>
              <p className="mt-1 text-sm text-muted-foreground">
                {passwordPrompt.reason === PasswordResponses.INCORRECT_PASSWORD ? "密码不正确，请重试。" : "请输入文档打开密码。"}
              </p>
            </div>
            <Input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="PDF 密码"
              aria-label="PDF 密码"
              autoFocus
            />
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => {
                passwordPrompt.callback(null);
                setPasswordPrompt(null);
              }}>取消</Button>
              <Button type="submit">打开</Button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
