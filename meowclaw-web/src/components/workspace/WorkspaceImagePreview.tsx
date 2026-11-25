import { useEffect, useRef } from "react";

export function WorkspaceImagePreview({ blob, name }: { blob: Blob; name: string }) {
  const imageRef = useRef<HTMLImageElement>(null);

  useEffect(() => {
    const image = imageRef.current;
    const objectUrl = URL.createObjectURL(blob);
    image?.setAttribute("src", objectUrl);
    return () => {
      image?.removeAttribute("src");
      URL.revokeObjectURL(objectUrl);
    };
  }, [blob]);

  return (
    <div className="flex min-h-0 flex-1 justify-center overflow-auto rounded-lg bg-muted/20 p-2">
      <img
        ref={imageRef}
        alt={name}
        className="m-auto max-h-[70dvh] max-w-full rounded object-contain"
      />
    </div>
  );
}
