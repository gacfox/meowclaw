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
  group?: string;
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

  const groups = React.useMemo(() => {
    const map = new Map<string, MultiSelectOption[]>();
    for (const option of options) {
      const key = option.group ?? "";
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(option);
    }
    return [...map.entries()];
  }, [options]);

  const setGroupSelected = (
    groupOptions: MultiSelectOption[],
    checked: boolean
  ) => {
    const values = groupOptions.map((option) => option.value);
    onChange(
      checked
        ? [...new Set([...selected, ...values])]
        : selected.filter((v) => !values.includes(v))
    );
  };

  const renderOption = (option: MultiSelectOption) => {
    const isSelected = selected.includes(option.value);
    return (
      <CommandItem
        key={option.value}
        value={option.value}
        keywords={[option.label, option.group ?? "", option.description ?? ""]}
        onSelect={() => toggle(option.value)}
      >
        <Check
          className={cn(
            "mr-2 size-4 shrink-0",
            isSelected ? "opacity-100" : "opacity-0"
          )}
        />
        <div className="flex min-w-0 flex-col">
          <span className="truncate" title={option.label}>
            {option.label}
          </span>
          {option.description && (
            <span
              className="truncate text-xs text-muted-foreground"
              title={option.description}
            >
              {option.description}
            </span>
          )}
        </div>
      </CommandItem>
    );
  };

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
        className="flex w-[--radix-popover-trigger-width] max-w-lg flex-col overflow-hidden p-0 max-h-96"
        onWheel={(e) => e.stopPropagation()}
      >
        <Command className="flex h-full flex-col overflow-hidden">
          <CommandInput placeholder={searchPlaceholder} />
          <CommandList className="max-h-none flex-1 overflow-y-auto">
            <CommandEmpty>{emptyText}</CommandEmpty>
            {groups.map(([groupName, groupOptions]) => {
              const allSelected = groupOptions.every((option) =>
                selected.includes(option.value)
              );
              return (
                <CommandGroup
                  key={groupName}
                  heading={
                    groupName ? (
                      <div className="flex items-center justify-between gap-2">
                        <span className="truncate" title={groupName}>
                          {groupName}
                        </span>
                        <span
                          role="button"
                          tabIndex={0}
                          onClick={() =>
                            setGroupSelected(groupOptions, !allSelected)
                          }
                          onKeyDown={(e) => {
                            if (e.key === "Enter" || e.key === " ") {
                              e.preventDefault();
                              setGroupSelected(groupOptions, !allSelected);
                            }
                          }}
                          className="shrink-0 cursor-pointer text-primary hover:underline"
                        >
                          {allSelected ? "取消全选" : "全选"}
                        </span>
                      </div>
                    ) : undefined
                  }
                >
                  {groupOptions.map(renderOption)}
                </CommandGroup>
              );
            })}
          </CommandList>
          <div className="flex items-center justify-between border-t px-3 py-1.5 text-xs text-muted-foreground">
            <span>已选 {selected.length} 项</span>
            <div className="flex items-center gap-3">
              <button
                type="button"
                className="hover:text-foreground"
                onClick={() => onChange(options.map((option) => option.value))}
              >
                全选
              </button>
              <button
                type="button"
                className="hover:text-foreground disabled:pointer-events-none disabled:opacity-50"
                disabled={selected.length === 0}
                onClick={() => onChange([])}
              >
                清空
              </button>
            </div>
          </div>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
