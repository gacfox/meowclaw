import * as React from "react";
import { Check, ChevronDown, X } from "lucide-react";

import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";

export interface MultiSelectOption {
  value: string;
  label: string;
  description?: string;
}

interface MultiSelectProps {
  options: MultiSelectOption[];
  selected: string[];
  onChange: (selected: string[]) => void;
  placeholder?: string;
  searchPlaceholder?: string;
  emptyText?: string;
  className?: string;
}

export function MultiSelect({
  options,
  selected,
  onChange,
  placeholder = "选择...",
  searchPlaceholder = "搜索...",
  emptyText = "无可用选项",
  className,
}: MultiSelectProps) {
  const [open, setOpen] = React.useState(false);

  const toggle = (value: string) => {
    onChange(
      selected.includes(value)
        ? selected.filter((v) => v !== value)
        : [...selected, value]
    );
  };

  const remove = (e: React.MouseEvent, value: string) => {
    e.stopPropagation();
    onChange(selected.filter((v) => v !== value));
  };

  const selectedOptions = options.filter((option) =>
    selected.includes(option.value)
  );

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className={cn(
            "w-full justify-between px-3 py-2 h-auto min-h-10",
            className
          )}
        >
          <div className="flex flex-1 flex-nowrap items-center gap-1 overflow-hidden">
            {selectedOptions.length === 0 ? (
              <span className="text-muted-foreground">{placeholder}</span>
            ) : (
              <>
                {selectedOptions.slice(0, 1).map((option) => (
                  <Badge
                    key={option.value}
                    variant="secondary"
                    className="shrink-0 truncate max-w-[120px] gap-1 pr-1"
                  >
                    <span className="truncate">{option.label}</span>
                    <span
                      role="button"
                      tabIndex={0}
                      onClick={(e) => remove(e, option.value)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          remove(e as unknown as React.MouseEvent, option.value);
                        }
                      }}
                      className="rounded-sm outline-none hover:text-destructive focus-visible:ring-1"
                    >
                      <X className="size-3 shrink-0" />
                    </span>
                  </Badge>
                ))}
                {selectedOptions.length > 1 && (
                  <Badge variant="secondary" className="shrink-0">
                    +{selectedOptions.length - 1}
                  </Badge>
                )}
              </>
            )}
          </div>
          <ChevronDown className="ml-2 size-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        className="flex w-[--radix-popover-trigger-width] max-w-sm flex-col overflow-hidden p-0 max-h-80"
        onWheel={(e) => e.stopPropagation()}
      >
        <Command className="flex h-full flex-col overflow-hidden">
          <CommandInput placeholder={searchPlaceholder} />
          <CommandList className="flex-1 overflow-y-auto">
            <CommandEmpty>{emptyText}</CommandEmpty>
            <CommandGroup>
              {options.map((option) => {
                const isSelected = selected.includes(option.value);
                return (
                  <CommandItem
                    key={option.value}
                    value={option.value}
                    onSelect={() => toggle(option.value)}
                  >
                    <Check
                      className={cn(
                        "mr-2 size-4",
                        isSelected ? "opacity-100" : "opacity-0"
                      )}
                    />
                    <div className="flex flex-col">
                      <span className="break-words">{option.label}</span>
                      {option.description && (
                        <span className="break-words text-xs text-muted-foreground">
                          {option.description}
                        </span>
                      )}
                    </div>
                  </CommandItem>
                );
              })}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
