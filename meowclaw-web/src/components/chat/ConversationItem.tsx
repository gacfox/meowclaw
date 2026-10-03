import type { ConversationDTO, ProjectDTO } from "@/types";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import { ChevronRight, Folder, FolderInput, FolderMinus, Loader2, MoreHorizontal, Pencil, Pin, PinOff, SquarePen, Trash2 } from "lucide-react";
import type { ReactNode } from "react";

export type ConversationLocation = "pinned" | "project" | "other";

export function ConversationGroupHeader({
  label,
  collapsed,
  onToggle,
  actions,
}: {
  label: string;
  collapsed: boolean;
  onToggle: () => void;
  actions?: ReactNode;
}) {
  return (
    <div className="flex items-center gap-1 px-3 py-1.5 text-xs text-muted-foreground">
      <button type="button" className="flex flex-1 items-center gap-1 text-left" onClick={onToggle}>
        <ChevronRight className={cn("size-3 transition-transform", !collapsed && "rotate-90")} />
        {label}
      </button>
      {actions}
    </div>
  );
}

export function ProjectRow({
  project,
  collapsed,
  renaming,
  renameDraft,
  onToggle,
  onNewConversation,
  onRenameStart,
  onRenameChange,
  onRenameConfirm,
  onRenameCancel,
  onDelete,
}: {
  project: ProjectDTO;
  collapsed: boolean;
  renaming: boolean;
  renameDraft: string;
  onToggle: () => void;
  onNewConversation: () => void;
  onRenameStart: () => void;
  onRenameChange: (value: string) => void;
  onRenameConfirm: () => void;
  onRenameCancel: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="group flex items-center gap-1 px-3 py-1.5 text-sm">
      {renaming ? (
        <input
          autoFocus
          value={renameDraft}
          onChange={(e) => onRenameChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") onRenameConfirm();
            if (e.key === "Escape") onRenameCancel();
          }}
          onBlur={onRenameConfirm}
          className="flex-1 rounded bg-background px-1.5 py-0.5 text-sm outline-none ring-1 ring-ring"
        />
      ) : (
        <>
          <button type="button" className="flex flex-1 items-center gap-1.5 text-left" onClick={onToggle}>
            <ChevronRight className={cn("size-3 shrink-0 transition-transform", !collapsed && "rotate-90")} />
            <Folder className="size-3.5 shrink-0 text-muted-foreground" />
            <span className="truncate">{project.name}</span>
          </button>
          <Button
            variant="ghost"
            size="icon-xs"
            className="opacity-0 group-hover:opacity-100"
            title="在该项目中新建会话"
            onClick={onNewConversation}
          >
            <SquarePen className="size-3.5" />
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-xs" className="opacity-0 group-hover:opacity-100">
                <MoreHorizontal className="size-3.5" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start">
              <DropdownMenuItem onClick={onRenameStart}>
                <Pencil className="size-3.5" />
                重命名
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" onClick={onDelete}>
                <Trash2 className="size-3.5" />
                删除项目
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </>
      )}
    </div>
  );
}

export function ConversationItem({
  convo,
  location,
  active,
  busy,
  renaming,
  renameDraft,
  projects,
  onSelect,
  onRenameStart,
  onRenameChange,
  onRenameConfirm,
  onRenameCancel,
  onTogglePin,
  onMoveToProject,
  onDelete,
}: {
  convo: ConversationDTO;
  location: ConversationLocation;
  active: boolean;
  busy: boolean;
  renaming: boolean;
  renameDraft: string;
  projects: ProjectDTO[];
  onSelect: () => void;
  onRenameStart: () => void;
  onRenameChange: (value: string) => void;
  onRenameConfirm: () => void;
  onRenameCancel: () => void;
  onTogglePin: () => void;
  onMoveToProject: (projectId: number | null) => void;
  onDelete: () => void;
}) {
  return (
    <div
      className={cn(
        "group flex cursor-pointer items-center gap-1 py-1.5 pr-2 text-sm hover:bg-muted",
        location === "project" ? "pl-8" : "px-3",
        active && "bg-muted",
      )}
      onClick={() => { if (!renaming) onSelect(); }}
    >
      {renaming ? (
        <input
          autoFocus
          value={renameDraft}
          onChange={(e) => onRenameChange(e.target.value)}
          onClick={(e) => e.stopPropagation()}
          onKeyDown={(e) => {
            if (e.key === "Enter") onRenameConfirm();
            if (e.key === "Escape") onRenameCancel();
          }}
          onBlur={onRenameConfirm}
          className="flex-1 rounded bg-background px-1.5 py-0.5 text-sm outline-none ring-1 ring-ring"
        />
      ) : (
        <>
          <span className="flex-1 truncate">{convo.title ?? "新对话"}</span>
          {busy && <Loader2 className="size-3 shrink-0 animate-spin text-muted-foreground" />}
          {!busy && (
            <Button
              variant="ghost"
              size="icon-xs"
              className="opacity-0 group-hover:opacity-100"
              title={location === "pinned" ? "取消钉选" : "钉选"}
              onClick={(e) => { e.stopPropagation(); onTogglePin(); }}
            >
              {location === "pinned" ? <PinOff className="size-3" /> : <Pin className="size-3" />}
            </Button>
          )}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon-xs"
                className="opacity-0 group-hover:opacity-100"
                onClick={(e) => e.stopPropagation()}
              >
                <MoreHorizontal className="size-3.5" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" onClick={(e) => e.stopPropagation()}>
              <DropdownMenuItem onClick={onRenameStart}>
                <Pencil className="size-3.5" />
                重命名
              </DropdownMenuItem>
              <DropdownMenuItem onClick={onTogglePin}>
                {convo.pinned ? <PinOff className="size-3.5" /> : <Pin className="size-3.5" />}
                {convo.pinned ? "取消钉选" : "钉选"}
              </DropdownMenuItem>
              {location === "project" ? (
                <DropdownMenuItem onClick={() => onMoveToProject(null)}>
                  <FolderMinus className="size-3.5" />
                  从项目中移除
                </DropdownMenuItem>
              ) : (
                <DropdownMenuSub>
                  <DropdownMenuSubTrigger>
                    <FolderInput className="size-3.5" />
                    移入项目
                  </DropdownMenuSubTrigger>
                  <DropdownMenuSubContent>
                    {projects.length === 0 && (
                      <DropdownMenuItem disabled>暂无项目</DropdownMenuItem>
                    )}
                    {projects.map((p) => (
                      <DropdownMenuItem key={p.id} onClick={() => onMoveToProject(p.id)}>
                        <Folder className="size-3.5" />
                        {p.name}
                      </DropdownMenuItem>
                    ))}
                  </DropdownMenuSubContent>
                </DropdownMenuSub>
              )}
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" onClick={onDelete}>
                <Trash2 className="size-3.5" />
                删除
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </>
      )}
    </div>
  );
}
