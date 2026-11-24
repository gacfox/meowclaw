import type { FileEntry } from "@/types";
import {
  ClipboardPaste,
  Copy,
  Download,
  FilePlus,
  FolderPlus,
  Info,
  ListChecks,
  Pencil,
  RefreshCw,
  Scissors,
  Trash2,
  Upload,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";

interface WorkspaceTouchActionSheetProps {
  open: boolean;
  entry: FileEntry | null;
  targetCount: number;
  canPaste: boolean;
  operationPending: boolean;
  onOpenChange: (open: boolean) => void;
  onOpen: () => void;
  onDownload: () => void;
  onCopy: () => void;
  onCut: () => void;
  onPaste: () => void;
  onRename: () => void;
  onDelete: () => void;
  onProperties: () => void;
  onSelect: () => void;
  onCreate: (type: "FILE" | "DIR") => void;
  onUpload: () => void;
  onRefresh: () => void;
}

export function WorkspaceTouchActionSheet({
  open,
  entry,
  targetCount,
  canPaste,
  operationPending,
  onOpenChange,
  onOpen,
  onDownload,
  onCopy,
  onCut,
  onPaste,
  onRename,
  onDelete,
  onProperties,
  onSelect,
  onCreate,
  onUpload,
  onRefresh,
}: WorkspaceTouchActionSheetProps) {
  const run = (action: () => void) => {
    onOpenChange(false);
    action();
  };
  const actionClass = "h-11 justify-start px-3 text-base [&_svg]:size-4";

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="bottom"
        className="max-h-[85dvh] gap-0 rounded-t-xl pb-[max(0.5rem,env(safe-area-inset-bottom))]"
      >
        <SheetHeader className="pr-10">
          <SheetTitle className="truncate">{entry ? entry.name : "当前目录"}</SheetTitle>
          <SheetDescription>
            {entry
              ? targetCount > 1 ? `已选择 ${targetCount} 个项目` : "选择要执行的操作"
              : "新建、上传、粘贴或刷新当前目录"}
          </SheetDescription>
        </SheetHeader>
        <div className="grid gap-1 overflow-y-auto px-2 pb-2">
          {entry ? (
            <>
              <Button variant="ghost" className={actionClass} onClick={() => run(onSelect)}>
                <ListChecks />多选
              </Button>
              {targetCount === 1 && (
                <Button variant="ghost" className={actionClass} onClick={() => run(onOpen)}>打开</Button>
              )}
              <Button variant="ghost" className={actionClass} onClick={() => run(onDownload)}><Download />下载</Button>
              <Button variant="ghost" className={actionClass} onClick={() => run(onCopy)}><Copy />复制</Button>
              <Button variant="ghost" className={actionClass} onClick={() => run(onCut)}><Scissors />剪切</Button>
              <Button variant="ghost" className={actionClass} disabled={!canPaste || operationPending} onClick={() => run(onPaste)}>
                <ClipboardPaste />粘贴{entry.directory ? "到此文件夹" : ""}
              </Button>
              {targetCount === 1 && (
                <Button variant="ghost" className={actionClass} onClick={() => run(onRename)}><Pencil />重命名</Button>
              )}
              <Button variant="ghost" className={actionClass} onClick={() => run(onProperties)}><Info />属性</Button>
              <Button
                variant="ghost"
                className={`${actionClass} text-destructive hover:bg-destructive/10 hover:text-destructive`}
                disabled={operationPending}
                onClick={() => run(onDelete)}
              >
                <Trash2 />删除
              </Button>
            </>
          ) : (
            <>
              <Button variant="ghost" className={actionClass} disabled={!canPaste || operationPending} onClick={() => run(onPaste)}>
                <ClipboardPaste />粘贴到当前目录
              </Button>
              <Button variant="ghost" className={actionClass} onClick={() => run(() => onCreate("DIR"))}><FolderPlus />新建文件夹</Button>
              <Button variant="ghost" className={actionClass} onClick={() => run(() => onCreate("FILE"))}><FilePlus />新建文件</Button>
              <Button variant="ghost" className={actionClass} onClick={() => run(onUpload)}><Upload />上传</Button>
              <Button variant="ghost" className={actionClass} onClick={() => run(onRefresh)}><RefreshCw />刷新</Button>
            </>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
