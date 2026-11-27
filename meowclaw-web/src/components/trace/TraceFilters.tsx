import type { DateRange } from "react-day-picker";
import { format } from "date-fns";
import type { AgentDTO } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Calendar } from "@/components/ui/calendar";
import { CalendarIcon, RotateCcw, Search } from "lucide-react";

const ALL_VALUE = "ALL";
const STATUS_OPTIONS = [
  { value: ALL_VALUE, label: "全部" },
  { value: "COMPLETED", label: "已完成" },
  { value: "RUNNING", label: "运行中" },
  { value: "ERROR", label: "错误" },
];

export interface TraceFilterValues {
  agentId: string;
  status: string;
  keyword: string;
  range?: DateRange;
}

function formatRange(range?: DateRange): string {
  if (!range?.from) return "选择时间范围";
  const from = format(range.from, "yyyy-MM-dd");
  if (!range.to) return from;
  return `${from} ~ ${format(range.to, "yyyy-MM-dd")}`;
}

interface TraceFiltersProps {
  agents: AgentDTO[];
  filters: TraceFilterValues;
  onChange: (filters: TraceFilterValues) => void;
  onQuery: () => void;
  onReset: () => void;
}

export function TraceFilters({ agents, filters, onChange, onQuery, onReset }: TraceFiltersProps) {
  return (
    <div className="flex flex-wrap items-end gap-3 rounded-lg border bg-card p-4">
      <div className="flex min-w-[160px] flex-col gap-1.5">
        <Label className="text-xs text-muted-foreground">智能体</Label>
        <Select value={filters.agentId} onValueChange={(agentId) => onChange({ ...filters, agentId })}>
          <SelectTrigger className="w-[160px]"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_VALUE}>全部</SelectItem>
            {agents.map((agent) => (
              <SelectItem key={agent.id} value={String(agent.id)}>{agent.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="flex min-w-[120px] flex-col gap-1.5">
        <Label className="text-xs text-muted-foreground">状态</Label>
        <Select value={filters.status} onValueChange={(status) => onChange({ ...filters, status })}>
          <SelectTrigger className="w-[120px]"><SelectValue /></SelectTrigger>
          <SelectContent>
            {STATUS_OPTIONS.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="flex min-w-[200px] flex-col gap-1.5">
        <Label className="text-xs text-muted-foreground">关键字</Label>
        <div className="relative">
          <Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
          <Input
            placeholder="搜索用户输入"
            className="pl-9"
            value={filters.keyword}
            onChange={(event) => onChange({ ...filters, keyword: event.target.value })}
            onKeyDown={(event) => event.key === "Enter" && onQuery()}
          />
        </div>
      </div>

      <div className="flex flex-col gap-1.5">
        <Label className="text-xs text-muted-foreground">时间范围</Label>
        <Popover>
          <PopoverTrigger asChild>
            <Button variant="outline" className="w-[240px] justify-start text-left font-normal">
              <CalendarIcon className="mr-2 size-4" />
              {formatRange(filters.range)}
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-auto p-0" align="start">
            <Calendar
              mode="range"
              selected={filters.range}
              onSelect={(range) => onChange({ ...filters, range })}
            />
          </PopoverContent>
        </Popover>
      </div>

      <div className="flex items-center gap-2">
        <Button variant="outline" onClick={onReset}>
          <RotateCcw className="mr-1 size-4" />重置
        </Button>
        <Button onClick={onQuery}>
          <Search className="mr-1 size-4" />查询
        </Button>
      </div>
    </div>
  );
}
