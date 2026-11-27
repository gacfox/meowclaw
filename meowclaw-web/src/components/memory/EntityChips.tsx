import type { MemoryNodeDTO } from "@/types";
import { Badge } from "@/components/ui/badge";

export function EntityChips({ node }: { node: MemoryNodeDTO }) {
  if (!node.entities || node.entities.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-1">
      {node.entities.map((entity) => (
        <Badge key={entity.id} variant="outline" className="text-xs">{entity.name}</Badge>
      ))}
    </div>
  );
}
