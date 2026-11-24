import {
  File,
  FileArchive,
  FileCode,
  FileImage,
  FileMusic,
  FileSpreadsheet,
  FileTerminal,
  FileText,
  FileVideoCamera,
  Folder,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { workspaceFileIconKind } from "@/lib/workspaceFiles";

export function WorkspaceFileIcon({
  name,
  directory,
  className,
}: {
  name: string;
  directory: boolean;
  className?: string;
}) {
  const classes = cn("shrink-0", className);
  switch (workspaceFileIconKind(name, directory)) {
    case "folder": return <Folder className={cn(classes, "fill-amber-400/20 text-amber-500")} />;
    case "image": return <FileImage className={cn(classes, "text-sky-500")} />;
    case "audio": return <FileMusic className={cn(classes, "text-violet-500")} />;
    case "video": return <FileVideoCamera className={cn(classes, "text-rose-500")} />;
    case "archive": return <FileArchive className={cn(classes, "text-orange-500")} />;
    case "spreadsheet": return <FileSpreadsheet className={cn(classes, "text-emerald-600")} />;
    case "terminal": return <FileTerminal className={cn(classes, "text-slate-500")} />;
    case "code": return <FileCode className={cn(classes, "text-indigo-500")} />;
    case "text": return <FileText className={cn(classes, "text-muted-foreground")} />;
    default: return <File className={cn(classes, "text-muted-foreground")} />;
  }
}
