import type { MemoryNodeDTO } from "@/types";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { EntityChips } from "./EntityChips";
import { formatMemoryTimestamp, memoryTypeBadgeVariant, memoryTypeLabel } from "./memoryUtils";

export function MemoryCard({ node, onClick }: { node: MemoryNodeDTO; onClick: () => void }) {
  return (
    <Card className="cursor-pointer transition-shadow hover:shadow-md" onClick={onClick}>
      <CardContent className="flex flex-col gap-2 p-4">
        <div className="flex items-center justify-between">
          <Badge variant={memoryTypeBadgeVariant(node.type)}>{memoryTypeLabel(node.type)}</Badge>
          <span className="text-xs text-muted-foreground">{formatMemoryTimestamp(node.updatedAt)}</span>
        </div>
        <p className="line-clamp-3 whitespace-pre-wrap text-sm">{node.content}</p>
        <EntityChips node={node} />
      </CardContent>
    </Card>
  );
}
