import type { ReactNode } from "react";
import { createLowlight, common } from "lowlight";

const lowlight = createLowlight(common);

interface HastText {
  type: "text";
  value: string;
}

interface HastElement {
  type: "element";
  tagName: string;
  properties?: { className?: string[] };
  children: HastNode[];
}

type HastNode = HastText | HastElement;

function toReact(node: HastNode, index: number): ReactNode {
  if (node.type === "text") return node.value;
  return (
    <span key={index} className={node.properties?.className?.join(" ")}>
      {node.children.map(toReact)}
    </span>
  );
}

export function highlightJson(code: string): ReactNode[] {
  try {
    const tree = lowlight.highlight("json", code);
    return (tree.children as unknown as HastNode[]).map(toReact);
  } catch {
    return [code];
  }
}
