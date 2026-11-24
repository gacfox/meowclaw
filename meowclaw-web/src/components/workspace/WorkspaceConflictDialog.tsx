import { useState } from "react";
import type { ConflictStrategy } from "@/types";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

export function WorkspaceConflictDialog({
  name,
  showApplyAll = true,
  onChoose,
}: {
  name: string | null;
  showApplyAll?: boolean;
  onChoose: (strategy: ConflictStrategy, applyAll: boolean) => void;
}) {
  const [applyAll, setApplyAll] = useState(false);

  return (
    <Dialog open={name != null} onOpenChange={(open) => { if (!open && name != null) onChoose("SKIP", applyAll); }}>
      <DialogContent className="max-w-md" showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>目标位置已存在同名条目</DialogTitle>
          <DialogDescription className="break-all">
            「{name}」已存在。请选择本次操作的处理方式。
          </DialogDescription>
        </DialogHeader>
        {showApplyAll && (
          <div className="flex items-center gap-2">
            <Checkbox
              id="workspace-conflict-apply-all"
              checked={applyAll}
              onCheckedChange={(checked) => setApplyAll(checked === true)}
            />
            <Label htmlFor="workspace-conflict-apply-all">应用到本次队列中的后续冲突</Label>
          </div>
        )}
        <DialogFooter className="gap-2 sm:justify-between">
          <Button variant="outline" onClick={() => onChoose("SKIP", applyAll)}>跳过</Button>
          <div className="flex flex-col-reverse gap-2 sm:flex-row">
            <Button variant="outline" onClick={() => onChoose("RENAME", applyAll)}>保留两者</Button>
            <Button variant="destructive" onClick={() => onChoose("OVERWRITE", applyAll)}>覆盖</Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
