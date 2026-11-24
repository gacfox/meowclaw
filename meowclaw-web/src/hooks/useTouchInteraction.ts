import { useEffect, useState } from "react";

const TOUCH_INTERACTION_QUERY = "(pointer: coarse) and (hover: none)";

export function useTouchInteraction(onChange?: () => void): boolean {
  const [touchInteraction, setTouchInteraction] = useState(() =>
    typeof window !== "undefined" && window.matchMedia(TOUCH_INTERACTION_QUERY).matches);

  useEffect(() => {
    const mediaQuery = window.matchMedia(TOUCH_INTERACTION_QUERY);
    const handleChange = (event: MediaQueryListEvent) => {
      setTouchInteraction(event.matches);
      onChange?.();
    };
    mediaQuery.addEventListener("change", handleChange);
    return () => mediaQuery.removeEventListener("change", handleChange);
  }, [onChange]);

  return touchInteraction;
}
