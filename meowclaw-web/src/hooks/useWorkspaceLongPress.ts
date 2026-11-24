import { useCallback, useEffect, useRef } from "react";
import type { FileEntry } from "@/types";

const LONG_PRESS_DELAY = 550;
const MOVE_TOLERANCE = 10;
const CLICK_SUPPRESSION_WINDOW = 500;

interface PressState {
  entry: FileEntry;
  pointerId: number;
  startX: number;
  startY: number;
  timer: ReturnType<typeof setTimeout>;
}

export function useWorkspaceLongPress({
  enabled,
  onLongPress,
}: {
  enabled: boolean;
  onLongPress: (entry: FileEntry) => void;
}) {
  const pressRef = useRef<PressState | null>(null);
  const longPressTriggeredRef = useRef<string | null>(null);
  const suppressedClickRef = useRef<{ path: string; expiresAt: number } | null>(null);

  const cancelPress = useCallback(() => {
    if (pressRef.current) clearTimeout(pressRef.current.timer);
    pressRef.current = null;
  }, []);

  const clearGesture = useCallback(() => {
    cancelPress();
    longPressTriggeredRef.current = null;
    suppressedClickRef.current = null;
  }, [cancelPress]);

  useEffect(() => {
    if (!enabled) clearGesture();
  }, [enabled, clearGesture]);

  useEffect(() => clearGesture, [clearGesture]);

  const getLongPressProps = useCallback((entry: FileEntry) => {
    if (!enabled) return {};
    return {
      onPointerDown: (event: React.PointerEvent) => {
        if (!event.isPrimary || event.pointerType === "mouse" || event.button !== 0) return;
        cancelPress();
        longPressTriggeredRef.current = null;
        suppressedClickRef.current = null;
        const timeout = setTimeout(() => {
          longPressTriggeredRef.current = entry.path;
          suppressedClickRef.current = { path: entry.path, expiresAt: Number.POSITIVE_INFINITY };
          pressRef.current = null;
          onLongPress(entry);
        }, LONG_PRESS_DELAY);
        pressRef.current = {
          entry,
          pointerId: event.pointerId,
          startX: event.clientX,
          startY: event.clientY,
          timer: timeout,
        };
      },
      onPointerMove: (event: React.PointerEvent) => {
        const press = pressRef.current;
        if (!press || press.pointerId !== event.pointerId || press.entry.path !== entry.path) return;
        if (Math.hypot(event.clientX - press.startX, event.clientY - press.startY) > MOVE_TOLERANCE) {
          cancelPress();
        }
      },
      onPointerUp: () => {
        if (longPressTriggeredRef.current === entry.path) {
          suppressedClickRef.current = {
            path: entry.path,
            expiresAt: Date.now() + CLICK_SUPPRESSION_WINDOW,
          };
          longPressTriggeredRef.current = null;
        }
        cancelPress();
      },
      onPointerCancel: clearGesture,
      onPointerLeave: clearGesture,
      onLostPointerCapture: clearGesture,
    };
  }, [enabled, cancelPress, clearGesture, onLongPress]);

  const shouldSuppressClick = useCallback((path: string) => {
    const suppressed = suppressedClickRef.current;
    if (!suppressed || suppressed.path !== path || suppressed.expiresAt < Date.now()) return false;
    suppressedClickRef.current = null;
    return true;
  }, []);

  return { getLongPressProps, shouldSuppressClick };
}
