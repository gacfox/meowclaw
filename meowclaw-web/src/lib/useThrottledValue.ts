import { useEffect, useRef, useState } from "react";

export function useThrottledValue<T>(value: T, intervalMs: number): T {
  const [throttled, setThrottled] = useState(value);
  const latestRef = useRef(value);
  const lastUpdateRef = useRef(0);

  useEffect(() => {
    latestRef.current = value;
  }, [value]);

  useEffect(() => {
    const remaining = Math.max(0, intervalMs - (Date.now() - lastUpdateRef.current));
    const timer = setTimeout(() => {
      lastUpdateRef.current = Date.now();
      setThrottled(latestRef.current);
    }, remaining);
    return () => clearTimeout(timer);
  }, [value, intervalMs]);

  return throttled;
}
