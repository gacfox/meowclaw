import { useEffect, useRef } from "react";
import * as echarts from "echarts";
import type { EChartsOption } from "echarts";
import { useTheme } from "next-themes";
import type { MemoryGraph } from "@/types";
import { MEMORY_GRAPH_COLORS, memoryTypeLabel, truncateMemoryLabel } from "./memoryUtils";

export function MemoryGraphView({ graph, onMemoryClick }: { graph: MemoryGraph; onMemoryClick: (memoryId: number) => void }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<echarts.ECharts | null>(null);
  const onMemoryClickRef = useRef(onMemoryClick);
  const { resolvedTheme } = useTheme();

  useEffect(() => {
    onMemoryClickRef.current = onMemoryClick;
  }, [onMemoryClick]);

  useEffect(() => {
    if (!containerRef.current) return;
    const chart = echarts.init(containerRef.current);
    chartRef.current = chart;
    chart.on("click", (params) => {
      const data = params.data as { memoryId?: number } | undefined;
      if (params.dataType === "node" && data?.memoryId != null) {
        onMemoryClickRef.current(data.memoryId);
      }
    });
    const resizeObserver = new ResizeObserver(() => chart.resize());
    resizeObserver.observe(containerRef.current);
    return () => {
      resizeObserver.disconnect();
      chart.dispose();
      chartRef.current = null;
    };
  }, []);

  useEffect(() => {
    const chart = chartRef.current;
    if (!chart) return;
    const colors = MEMORY_GRAPH_COLORS[resolvedTheme === "dark" ? "dark" : "light"];
    const nodes = [
      ...graph.memories.map((m) => ({
        id: `m-${m.id}`,
        name: truncateMemoryLabel(m.content, 12),
        memoryId: m.id,
        category: memoryTypeLabel(m.type),
        symbol: "circle",
        symbolSize: 26,
      })),
      ...graph.entities.map((e) => ({
        id: `e-${e.id}`,
        name: truncateMemoryLabel(e.name, 12),
        category: "实体",
        symbol: "circle",
        symbolSize: 20,
      })),
    ];
    const links = graph.relations.map((r) => ({
      source: `m-${r.memoryId}`,
      target: `e-${r.entityId}`,
      value: r.description,
    }));
    const option: EChartsOption = {
      tooltip: {
        formatter: (params: unknown) => {
          const p = params as { dataType: string; data?: { value?: string }; name: string };
          if (p.dataType === "edge") return p.data?.value ?? "";
          return p.name;
        },
      },
      legend: {
        data: ["事实", "偏好", "规则", "实体"],
        bottom: 0,
        icon: "circle",
        textStyle: { fontSize: 10 },
      },
      series: [{
        type: "graph",
        layout: "force",
        roam: true,
        draggable: true,
        force: { repulsion: 220, edgeLength: 100 },
        label: { show: true, fontSize: 10 },
        itemStyle: {
          borderColor: resolvedTheme === "dark" ? "#0f172a" : "#1e293b",
          borderWidth: 2,
        },
        categories: [
          { name: "事实", itemStyle: { color: colors.fact } },
          { name: "偏好", itemStyle: { color: colors.preference } },
          { name: "规则", itemStyle: { color: colors.rule } },
          { name: "实体", itemStyle: { color: colors.entity } },
        ],
        data: nodes,
        links,
        lineStyle: { color: "source", opacity: 0.4, curveness: 0.1 },
        emphasis: { focus: "adjacency", itemStyle: { borderWidth: 2 } },
      }] as EChartsOption["series"],
    };
    chart.setOption(option, true);
  }, [graph, resolvedTheme]);

  return (
    <div className="relative">
      <div ref={containerRef} className="h-[55dvh] min-h-80 w-full rounded-lg border md:h-[600px]" />
      {graph.memories.length === 0 && (
        <div className="absolute inset-0 flex items-center justify-center text-muted-foreground">暂无记忆数据</div>
      )}
    </div>
  );
}
