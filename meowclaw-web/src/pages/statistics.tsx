import React, { useState, useEffect, useMemo, useCallback } from "react";
import ReactECharts from "echarts-for-react";
import {
  CalendarIcon,
  Cpu,
  MessageSquare,
  ArrowUpFromLine,
  ArrowDownToLine,
} from "lucide-react";
import { format, subDays, startOfDay, endOfDay } from "date-fns";
import { zhCN } from "date-fns/locale";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Spinner } from "@/components/ui/spinner";
import {
  statisticsService,
  type StatisticsOverviewDto,
  type DailyStatisticsDto,
  type ModelInfo,
} from "@/services/statistics";

const TIME_RANGE_OPTIONS = [
  { label: "近7天", value: "7" },
  { label: "近30天", value: "30" },
  { label: "近90天", value: "90" },
  { label: "自定义", value: "custom" },
];

const CHART_COLORS = [
  "#5470c6",
  "#91cc75",
  "#fac858",
  "#ee6666",
  "#73c0de",
  "#3ba272",
  "#fc8452",
  "#9a60b4",
  "#ea7ccc",
  "#48b8d0",
];

function formatNumber(num: number): string {
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + "M";
  }
  if (num >= 1000) {
    return (num / 1000).toFixed(1) + "K";
  }
  return num.toString();
}

function getModelKey(apiUrl: string, model: string): string {
  return `${apiUrl}||${model}`;
}

export const StatisticsPage: React.FC = () => {
  const [overview, setOverview] = useState<StatisticsOverviewDto | null>(null);
  const [dailyData, setDailyData] = useState<DailyStatisticsDto[]>([]);
  const [modelInfos, setModelInfos] = useState<ModelInfo[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const [selectedModelKey, setSelectedModelKey] = useState<string>("all");
  const [timeRange, setTimeRange] = useState<string>("7");
  const [customDateRange, setCustomDateRange] = useState<{
    from: Date | undefined;
    to: Date | undefined;
  }>({ from: undefined, to: undefined });
  const [isCalendarOpen, setIsCalendarOpen] = useState(false);

  const getTimeRange = useCallback(() => {
    if (timeRange === "custom" && customDateRange.from && customDateRange.to) {
      return {
        startTime: startOfDay(customDateRange.from).getTime(),
        endTime: endOfDay(customDateRange.to).getTime(),
      };
    }

    const days = parseInt(timeRange) || 7;
    const endTime = endOfDay(new Date()).getTime();
    const startTime = startOfDay(subDays(new Date(), days - 1)).getTime();

    return { startTime, endTime };
  }, [timeRange, customDateRange.from, customDateRange.to]);

  const loadModels = useCallback(async () => {
    try {
      const response = await statisticsService.getAvailableModels();
      if (response.code === 200 && response.data) {
        setModelInfos(response.data);
      }
    } catch (error) {
      console.error("加载模型列表失败", error);
    }
  }, []);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    try {
      const { startTime, endTime } = getTimeRange();

      let apiUrl: string | undefined;
      let model: string | undefined;

      if (selectedModelKey !== "all") {
        const parts = selectedModelKey.split("||");
        if (parts.length === 2) {
          apiUrl = parts[0];
          model = parts[1];
        }
      }

      const [overviewRes, dailyRes] = await Promise.all([
        statisticsService.getOverview({ startTime, endTime }),
        statisticsService.getDailyStatistics({
          startTime,
          endTime,
          apiUrl,
          model,
        }),
      ]);

      if (overviewRes.code === 200 && overviewRes.data) {
        setOverview(overviewRes.data);
      }
      if (dailyRes.code === 200 && dailyRes.data) {
        setDailyData(dailyRes.data);
      }
    } catch (error) {
      console.error("加载统计数据失败", error);
    } finally {
      setIsLoading(false);
    }
  }, [getTimeRange, selectedModelKey]);

  useEffect(() => {
    loadModels();
  }, [loadModels]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const chartData = useMemo(() => {
    const dates = [...new Set(dailyData.map((d) => d.date))].sort();
    const keys =
      selectedModelKey === "all"
        ? [...new Set(dailyData.map((d) => getModelKey(d.apiUrl, d.model)))]
        : [selectedModelKey];

    const buildSeries = (getValue: (item: DailyStatisticsDto) => number) =>
      keys.map((key, index) => {
        const [apiUrl, model] = key.split("||");
        const info = modelInfos.find(
          (m) => m.apiUrl === apiUrl && m.model === model,
        );
        const displayName = info?.displayName || model;

        return {
          name: displayName,
          key,
          type: "line" as const,
          data: dates.map((date) => {
            const item = dailyData.find(
              (d) => d.date === date && getModelKey(d.apiUrl, d.model) === key,
            );
            return getValue(item!) || 0;
          }),
          smooth: true,
          lineStyle: { width: 2 },
          itemStyle: { color: CHART_COLORS[index % CHART_COLORS.length] },
        };
      });

    const inputTokensSeries = buildSeries((item) => item?.inputTokens || 0);
    const outputTokensSeries = buildSeries((item) => item?.outputTokens || 0);
    const messageSeries = buildSeries((item) => item?.messageCount || 0);

    return { dates, inputTokensSeries, outputTokensSeries, messageSeries };
  }, [dailyData, selectedModelKey, modelInfos]);

  const getChartOption = (
    title: string,
    dates: string[],
    series: typeof chartData.inputTokensSeries,
    yAxisName: string,
  ) => ({
    title: {
      text: title,
      left: "center",
      textStyle: { fontSize: 14, fontWeight: 500 },
    },
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "cross" },
    },
    legend: {
      top: 30,
      data: series.map((s) => s.name),
      type: "scroll",
    },
    grid: {
      top: 80,
      left: 60,
      right: 30,
      bottom: 30,
    },
    xAxis: {
      type: "category",
      data: dates,
      axisLabel: {
        formatter: (value: string) => value.slice(5),
      },
    },
    yAxis: {
      type: "value",
      name: yAxisName,
      axisLabel: {
        formatter: (value: number) => formatNumber(value),
      },
    },
    series,
  });

  const handleDateRangeSelect = (
    range: { from?: Date; to?: Date } | undefined,
  ) => {
    if (range?.from && range?.to) {
      setCustomDateRange({ from: range.from, to: range.to });
      setIsCalendarOpen(false);
    }
  };

  const displayDateRange = () => {
    if (timeRange === "custom" && customDateRange.from && customDateRange.to) {
      return `${format(customDateRange.from, "MM/dd", { locale: zhCN })} - ${format(customDateRange.to, "MM/dd", { locale: zhCN })}`;
    }
    return (
      TIME_RANGE_OPTIONS.find((o) => o.value === timeRange)?.label || "近7天"
    );
  };

  return (
    <div className="p-6 h-full flex flex-col min-h-0 overflow-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold">统计信息</h1>
        <p className="text-sm text-muted-foreground mt-1">查看使用统计数据</p>
      </div>

      <div className="flex flex-wrap gap-4 mb-6">
        <Select value={selectedModelKey} onValueChange={setSelectedModelKey}>
          <SelectTrigger className="w-48">
            <SelectValue placeholder="选择模型" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部模型</SelectItem>
            {modelInfos.map((info) => (
              <SelectItem
                key={getModelKey(info.apiUrl, info.model)}
                value={getModelKey(info.apiUrl, info.model)}
              >
                {info.displayName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={timeRange} onValueChange={setTimeRange}>
          <SelectTrigger className="w-32">
            <SelectValue placeholder="时间范围" />
          </SelectTrigger>
          <SelectContent>
            {TIME_RANGE_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {timeRange === "custom" && (
          <Popover open={isCalendarOpen} onOpenChange={setIsCalendarOpen}>
            <PopoverTrigger asChild>
              <Button
                variant="outline"
                className="w-56 justify-start text-left font-normal"
              >
                <CalendarIcon className="mr-2 h-4 w-4" />
                {displayDateRange()}
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-auto p-0" align="start">
              <Calendar
                mode="range"
                defaultMonth={customDateRange.from}
                selected={{
                  from: customDateRange.from,
                  to: customDateRange.to,
                }}
                onSelect={handleDateRangeSelect}
                numberOfMonths={2}
              />
            </PopoverContent>
          </Popover>
        )}
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center h-48">
          <Spinner className="size-8" />
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  配置的模型数
                </CardTitle>
                <Cpu className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {overview?.modelCount ?? 0}
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  Tokens总用量
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="flex items-center gap-4">
                  <div className="flex items-center gap-1.5">
                    <ArrowUpFromLine className="h-4 w-4 text-green-500" />
                    <span className="text-sm text-muted-foreground">输入:</span>
                    <span className="text-lg font-semibold">
                      {formatNumber(overview?.totalInputTokens ?? 0)}
                    </span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <ArrowDownToLine className="h-4 w-4 text-blue-500" />
                    <span className="text-sm text-muted-foreground">输出:</span>
                    <span className="text-lg font-semibold">
                      {formatNumber(overview?.totalOutputTokens ?? 0)}
                    </span>
                  </div>
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  消息总数
                </CardTitle>
                <MessageSquare className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {formatNumber(overview?.totalMessages ?? 0)}
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="grid grid-cols-1 gap-6">
            <Card>
              <CardContent className="pt-4">
                <ReactECharts
                  option={getChartOption(
                    "每日输入Tokens",
                    chartData.dates,
                    chartData.inputTokensSeries,
                    "Tokens",
                  )}
                  style={{ height: 300 }}
                  opts={{ renderer: "canvas" }}
                />
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-4">
                <ReactECharts
                  option={getChartOption(
                    "每日输出Tokens",
                    chartData.dates,
                    chartData.outputTokensSeries,
                    "Tokens",
                  )}
                  style={{ height: 300 }}
                  opts={{ renderer: "canvas" }}
                />
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-4">
                <ReactECharts
                  option={getChartOption(
                    "每日消息数",
                    chartData.dates,
                    chartData.messageSeries,
                    "消息数",
                  )}
                  style={{ height: 300 }}
                  opts={{ renderer: "canvas" }}
                />
              </CardContent>
            </Card>
          </div>
        </>
      )}
    </div>
  );
};
