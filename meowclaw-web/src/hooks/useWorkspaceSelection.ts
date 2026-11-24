import { useCallback, useState } from "react";
import type { FileEntry } from "@/types";

export function useWorkspaceSelection(entries: FileEntry[]) {
  const [selectedPaths, setSelectedPaths] = useState<Set<string>>(() => new Set());
  const [anchorPath, setAnchorPath] = useState<string | null>(null);
  const [selectionMode, setSelectionMode] = useState(false);

  const clearSelection = useCallback(() => {
    setSelectedPaths(new Set());
    setAnchorPath(null);
    setSelectionMode(false);
  }, []);

  const selectSingle = useCallback((path: string) => {
    setSelectedPaths(new Set([path]));
    setAnchorPath(path);
  }, []);

  const selectAll = useCallback(() => {
    setSelectedPaths(new Set(entries.map((entry) => entry.path)));
  }, [entries]);

  const enterSelectionMode = useCallback((path?: string) => {
    setSelectionMode(true);
    if (path) {
      setSelectedPaths(new Set([path]));
      setAnchorPath(path);
    }
  }, []);

  const exitSelectionMode = useCallback(() => {
    setSelectionMode(false);
    setSelectedPaths(new Set());
    setAnchorPath(null);
  }, []);

  const toggleSelection = useCallback((path: string) => {
    setSelectedPaths((previous) => {
      const next = new Set(previous);
      if (next.has(path)) next.delete(path);
      else next.add(path);
      return next;
    });
    setAnchorPath(path);
  }, []);

  const pruneSelection = useCallback((nextEntries: FileEntry[]) => {
    const paths = new Set(nextEntries.map((entry) => entry.path));
    setSelectedPaths((previous) => {
      const next = new Set([...previous].filter((path) => paths.has(path)));
      return next.size === previous.size ? previous : next;
    });
    setAnchorPath((previous) => previous && paths.has(previous) ? previous : null);
  }, []);

  const handleEntryClick = useCallback((event: React.MouseEvent, entry: FileEntry) => {
    if (event.shiftKey) {
      const start = anchorPath ? entries.findIndex((item) => item.path === anchorPath) : -1;
      const end = entries.findIndex((item) => item.path === entry.path);
      if (start >= 0 && end >= 0) {
        const [from, to] = start <= end ? [start, end] : [end, start];
        setSelectedPaths(new Set(entries.slice(from, to + 1).map((item) => item.path)));
        return;
      }
    }

    if (event.ctrlKey || event.metaKey) {
      setSelectedPaths((previous) => {
        const next = new Set(previous);
        if (next.has(entry.path)) next.delete(entry.path);
        else next.add(entry.path);
        return next;
      });
      setAnchorPath(entry.path);
      return;
    }

    selectSingle(entry.path);
  }, [anchorPath, entries, selectSingle]);

  return {
    selectedPaths,
    anchorPath,
    selectionMode,
    clearSelection,
    selectSingle,
    selectAll,
    enterSelectionMode,
    exitSelectionMode,
    toggleSelection,
    pruneSelection,
    handleEntryClick,
  };
}
