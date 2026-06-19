import { useCallback, useEffect, useRef, useState } from "react";
import { useTheme } from "next-themes";
import type { EChartsOption } from "echarts";
import { endOfDay, format, startOfDay, subDays } from "date-fns";
import type { DateRange } from "react-day-picker";
import type { LlmDTO, TokenStatsDTO } from "@/types";
import { listLlms } from "@/services/llm";
import { getTokenStats } from "@/services/tokens";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { LineChart } from "@/components/charts/LineChart";
import {
  ArrowDown,
  ArrowUp,
  Calendar as CalendarIcon,
  Hash,
  LineChart as LineChartIcon,
  RefreshCw,
  Sigma,
  Trophy,
} from "lucide-react";
import { toast } from "sonner";

type Preset = "7d" | "30d" | "90d" | "custom";
type SeriesKey = "input" | "output" | "total" | "callCount";

const PRESETS: { value: Preset; label: string; days: number }[] = [
  { value: "7d", label: "近7天", days: 7 },
  { value: "30d", label: "近30天", days: 30 },
  { value: "90d", label: "近90天", days: 90 },
];

function rangeForDays(days: number): DateRange {
  const today = startOfDay(new Date());
  return { from: subDays(today, days - 1), to: today };
}

function formatRange(range?: DateRange): string {
  if (!range?.from) return "选择时间范围";
  const from = format(range.from, "yyyy-MM-dd");
  if (!range.to) return from;
  return `${from} ~ ${format(range.to, "yyyy-MM-dd")}`;
}

function formatNum(n: number): string {
  return n.toLocaleString("en-US");
}

export function TokensStatisticsPage() {
  const { resolvedTheme } = useTheme();

  const [preset, setPreset] = useState<Preset>("7d");
  const [range, setRange] = useState<DateRange | undefined>(() => rangeForDays(7));
  const [llmId, setLlmId] = useState<number | null>(null);

  const [llms, setLlms] = useState<LlmDTO[]>([]);
  const [data, setData] = useState<TokenStatsDTO | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    listLlms().then(setLlms).catch(() => undefined);
  }, []);

  const fetchStats = useCallback(async () => {
    if (!range?.from) return;
    setLoading(true);
    try {
      const res = await getTokenStats({
        start: range.from.getTime(),
        end: endOfDay(range.to ?? range.from).getTime(),
        llmId: llmId ?? undefined,
      });
      setData(res);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "加载统计数据失败");
    } finally {
      setLoading(false);
    }
  }, [range, llmId]);

  const didMount = useRef(false);
  useEffect(() => {
    if (didMount.current) return;
    didMount.current = true;
    fetchStats();
  }, [fetchStats]);

  const applyPreset = (p: Preset) => {
    const conf = PRESETS.find((it) => it.value === p);
    if (!conf) return;
    setPreset(p);
    setRange(rangeForDays(conf.days));
  };

  const onRangeSelect = (r?: DateRange) => {
    setPreset("custom");
    setRange(r);
  };

  const dark = resolvedTheme === "dark";
  const axisColor = dark ? "#64748b" : "#94a3b8";
  const textColor = dark ? "#cbd5e1" : "#475569";
  const splitColor = dark ? "#1e293b" : "#e2e8f0";

  const buildOption = useCallback(
    (metricKey: SeriesKey): EChartsOption => ({
      tooltip: { trigger: "axis" },
      legend: { type: "scroll", top: 0, textStyle: { color: textColor } },
      grid: { left: 8, right: 16, top: 36, bottom: 8, containLabel: true },
      xAxis: {
        type: "category",
        boundaryGap: false,
        data: data?.dates ?? [],
        axisLabel: { color: axisColor },
        axisLine: { lineStyle: { color: axisColor } },
      },
      yAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: { color: axisColor },
        splitLine: { lineStyle: { color: splitColor } },
      },
      series: (data?.modelSeries ?? []).map((s) => ({
        name: s.llmName,
        type: "line",
        smooth: true,
        symbol: "circle",
        symbolSize: 5,
        data: s[metricKey],
      })),
    }),
    [data, axisColor, textColor, splitColor],
  );

  const summary = data?.summary;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">tokens 统计</h1>
        <Button variant="outline" size="icon" onClick={fetchStats} title="刷新" disabled={loading}>
          <RefreshCw className={loading ? "size-4 animate-spin" : "size-4"} />
        </Button>
      </div>

      {/* 查询条件 */}
      <Card>
        <CardContent className="flex flex-wrap items-center gap-4 pt-6">
          <div className="flex items-center gap-2">
            {PRESETS.map((p) => (
              <Button
                key={p.value}
                size="sm"
                variant={preset === p.value ? "default" : "outline"}
                onClick={() => applyPreset(p.value)}
              >
                {p.label}
              </Button>
            ))}
          </div>
          <Popover>
            <PopoverTrigger asChild>
              <Button variant="outline" className="min-w-[230px] justify-start text-left font-normal">
                <CalendarIcon className="size-4" />
                {formatRange(range)}
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-auto p-0" align="start">
              <Calendar
                mode="range"
                selected={range}
                onSelect={onRangeSelect}
                numberOfMonths={2}
                disabled={{ after: new Date() }}
              />
            </PopoverContent>
          </Popover>
          <div className="flex items-center gap-2">
            <label className="text-sm text-muted-foreground">模型</label>
            <Select value={llmId == null ? "all" : llmId.toString()} onValueChange={(v) => setLlmId(v === "all" ? null : Number(v))}>
              <SelectTrigger className="w-[200px]">
                <SelectValue placeholder="全部模型" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部模型</SelectItem>
                {llms.map((llm) => (
                  <SelectItem key={llm.id} value={llm.id.toString()}>
                    {llm.name} ({llm.model})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <Button onClick={() => fetchStats()} disabled={loading}>
            {loading ? "查询中..." : "确定"}
          </Button>
        </CardContent>
      </Card>

      {/* 三卡片 */}
      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Tokens 汇总</CardTitle>
            <Sigma className="size-4 text-muted-foreground" />
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            <div className="flex items-center justify-between text-sm">
              <span className="flex items-center gap-1 text-muted-foreground">
                <ArrowDown className="size-3" /> 输入
              </span>
              <span className="font-semibold">{summary ? formatNum(summary.totalInputTokens) : "-"}</span>
            </div>
            <div className="flex items-center justify-between text-sm">
              <span className="flex items-center gap-1 text-muted-foreground">
                <ArrowUp className="size-3" /> 输出
              </span>
              <span className="font-semibold">{summary ? formatNum(summary.totalOutputTokens) : "-"}</span>
            </div>
            <div className="flex items-center justify-between border-t pt-2 text-sm">
              <span className="text-muted-foreground">合计</span>
              <span className="font-semibold">{summary ? formatNum(summary.totalTokens) : "-"}</span>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">LLM API 调用量</CardTitle>
            <Hash className="size-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{summary ? formatNum(summary.callCount) : "-"}</div>
            <p className="text-xs text-muted-foreground">筛选条件下的调用次数</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Top3 模型（按调用量）</CardTitle>
            <Trophy className="size-4 text-muted-foreground" />
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            {(data?.topModels ?? []).length === 0 ? (
              <div className="text-sm text-muted-foreground">暂无数据</div>
            ) : (
              (data?.topModels ?? []).map((m, i) => (
                <div key={`${m.llmId}-${i}`} className="flex items-center justify-between gap-2 text-sm">
                  <span className="flex min-w-0 items-center gap-2">
                    <span className="font-semibold text-muted-foreground">{i + 1}</span>
                    <span className="truncate">{m.llmName}</span>
                  </span>
                  <span className="shrink-0 font-semibold">{formatNum(m.callCount)} 次</span>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </div>

      {/* 四张折线图 */}
      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard title="Tokens 消耗 · 输入 (input)">
          <LineChart option={buildOption("input")} />
        </ChartCard>
        <ChartCard title="Tokens 消耗 · 输出 (output)">
          <LineChart option={buildOption("output")} />
        </ChartCard>
        <ChartCard title="Tokens 消耗 · 合计 (total)">
          <LineChart option={buildOption("total")} />
        </ChartCard>
        <ChartCard title="调用量">
          <LineChart option={buildOption("callCount")} />
        </ChartCard>
      </div>
    </div>
  );
}

function ChartCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center gap-2 space-y-0 pb-2">
        <LineChartIcon className="size-4 text-muted-foreground" />
        <CardTitle className="text-sm font-medium">{title}</CardTitle>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}
