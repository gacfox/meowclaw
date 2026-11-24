import type { FileEntry } from "@/types";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Checkbox } from "@/components/ui/checkbox";
import { cn } from "@/lib/utils";
import { fileTypeLabel, formatFileSize } from "@/lib/workspaceFiles";
import { useWorkspaceLongPress } from "@/hooks/useWorkspaceLongPress";
import { WorkspaceFileIcon } from "./WorkspaceFileIcon";

export type WorkspaceViewMode = "large" | "medium" | "details";

interface WorkspaceEntryViewProps {
  entries: FileEntry[];
  viewMode: WorkspaceViewMode;
  selectedPaths: Set<string>;
  cutPaths: Set<string>;
  touchMode: boolean;
  selectionMode: boolean;
  onEntryClick: (event: React.MouseEvent, entry: FileEntry) => void;
  onToggleSelect: (entry: FileEntry) => void;
  onOpen: (entry: FileEntry) => void;
  onContextEntry: (entry: FileEntry) => void;
  onLongPress: (entry: FileEntry) => void;
}

export function WorkspaceEntryView({
  entries,
  viewMode,
  selectedPaths,
  cutPaths,
  touchMode,
  selectionMode,
  onEntryClick,
  onToggleSelect,
  onOpen,
  onContextEntry,
  onLongPress,
}: WorkspaceEntryViewProps) {
  const { getLongPressProps, shouldSuppressClick } = useWorkspaceLongPress({
    enabled: touchMode && !selectionMode,
    onLongPress,
  });

  const entryProps = (entry: FileEntry) => ({
    "data-workspace-entry": true,
    tabIndex: 0,
    "aria-selected": selectedPaths.has(entry.path),
    onClick: (event: React.MouseEvent) => {
      if (!touchMode) {
        onEntryClick(event, entry);
        return;
      }
      if (shouldSuppressClick(entry.path)) {
        event.preventDefault();
        return;
      }
      if (selectionMode) onToggleSelect(entry);
      else onOpen(entry);
    },
    onDoubleClick: touchMode ? undefined : () => onOpen(entry),
    onContextMenu: (event: React.MouseEvent) => {
      if (touchMode) {
        event.preventDefault();
        return;
      }
      onContextEntry(entry);
    },
    onKeyDown: (event: React.KeyboardEvent) => {
      if (event.key === "Enter") {
        event.preventDefault();
        onOpen(entry);
      } else if (selectionMode && event.key === " ") {
        event.preventDefault();
        onToggleSelect(entry);
      }
    },
  });

  const selectionCheckbox = (entry: FileEntry) => touchMode && selectionMode ? (
    <Checkbox
      checked={selectedPaths.has(entry.path)}
      onCheckedChange={() => onToggleSelect(entry)}
      onClick={(event) => event.stopPropagation()}
      onKeyDown={(event) => event.stopPropagation()}
      aria-label={`选择 ${entry.name}`}
      className="shrink-0"
    />
  ) : null;

  if (viewMode === "details") {
    return (
      <Table>
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            <TableHead>名称</TableHead>
            <TableHead className="w-28">大小</TableHead>
            <TableHead className="w-32">类型</TableHead>
            <TableHead className="w-48">修改时间</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {entries.map((entry) => (
            <TableRow
              key={entry.path}
              {...entryProps(entry)}
              {...getLongPressProps(entry)}
              className={cn(
                "cursor-default select-none outline-none focus-visible:bg-muted",
                touchMode && "touch-pan-y [-webkit-touch-callout:none]",
                selectedPaths.has(entry.path) && "bg-primary/10 hover:bg-primary/15",
                cutPaths.has(entry.path) && "opacity-50",
              )}
            >
              <TableCell>
                <div className="flex min-w-0 items-center gap-2">
                  {selectionCheckbox(entry)}
                  <WorkspaceFileIcon name={entry.name} directory={entry.directory} className="size-4" />
                  <span className="truncate" title={entry.name}>{entry.name}</span>
                </div>
              </TableCell>
              <TableCell className="text-muted-foreground">
                {entry.directory ? "-" : formatFileSize(entry.size)}
              </TableCell>
              <TableCell className="text-muted-foreground">{fileTypeLabel(entry.name, entry.directory)}</TableCell>
              <TableCell className="text-muted-foreground">
                {entry.lastModified ? new Date(entry.lastModified).toLocaleString() : "-"}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    );
  }

  const large = viewMode === "large";
  return (
    <div
      className={cn(
        "grid gap-2 p-2",
        large
          ? "grid-cols-[repeat(auto-fill,minmax(9rem,1fr))]"
          : "grid-cols-[repeat(auto-fill,minmax(6.5rem,1fr))]",
      )}
    >
      {entries.map((entry) => (
        <div
          key={entry.path}
          {...entryProps(entry)}
          {...getLongPressProps(entry)}
          role="button"
          title={entry.path}
          className={cn(
            "relative flex cursor-default select-none flex-col items-center justify-center gap-2 rounded-lg border border-transparent text-center outline-none",
            "hover:bg-muted/70 focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/30",
            touchMode && "touch-pan-y [-webkit-touch-callout:none]",
            large ? "min-h-32 p-4" : "min-h-24 p-2.5",
            selectedPaths.has(entry.path) && "border-primary/25 bg-primary/10 hover:bg-primary/15",
            cutPaths.has(entry.path) && "opacity-50",
          )}
        >
          {touchMode && selectionMode && (
            <span className="absolute top-2 left-2 z-10 rounded bg-background/80 p-0.5">
              {selectionCheckbox(entry)}
            </span>
          )}
          <WorkspaceFileIcon
            name={entry.name}
            directory={entry.directory}
            className={large ? "size-14" : "size-9"}
          />
          <span className={cn("w-full break-words text-sm", large ? "line-clamp-3" : "line-clamp-2")}>
            {entry.name}
          </span>
        </div>
      ))}
    </div>
  );
}
