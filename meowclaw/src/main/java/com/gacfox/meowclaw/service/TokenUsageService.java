package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.TokenStatsDTO;
import com.gacfox.meowclaw.dto.TokenStatsDTO.TokenModelSeries;
import com.gacfox.meowclaw.dto.TokenStatsDTO.TokenStatsSummary;
import com.gacfox.meowclaw.dto.TokenStatsDTO.TokenTopModel;
import com.gacfox.meowclaw.dto.TokenUsageStatsRow;
import com.gacfox.meowclaw.entity.Llm;
import com.gacfox.meowclaw.entity.TokenUsage;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageContext;
import com.gacfox.meowclaw.repository.LlmRepository;
import com.gacfox.meowclaw.repository.TokenUsageRepository;
import com.gacfox.proarc.agentic.model.openai.ModelResponse;
import com.gacfox.proarc.agentic.model.openai.Usage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TokenUsageService {
    private final TokenUsageRepository tokenUsageRepository;
    private final LlmRepository llmRepository;

    @Autowired
    public TokenUsageService(TokenUsageRepository tokenUsageRepository, LlmRepository llmRepository) {
        this.tokenUsageRepository = tokenUsageRepository;
        this.llmRepository = llmRepository;
    }

    /**
     * 记录一次LLM调用的轻量统计事实，不依赖追踪明细的生命周期
     */
    @Transactional
    public void recordCall(TokenUsageContext ctx, ModelResponse response, String status) {
        Usage usage = response != null ? response.getUsage() : null;
        long input = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0L;
        long output = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0L;
        long total = usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens() : input + output;

        TokenUsage tokenUsage = new TokenUsage();
        tokenUsage.setLlmId(ctx.llmId());
        tokenUsage.setAgentId(ctx.agentId());
        tokenUsage.setModel(ctx.model());
        tokenUsage.setPurpose(ctx.purpose());
        tokenUsage.setStatus(status);
        tokenUsage.setInputTokens(input);
        tokenUsage.setOutputTokens(output);
        tokenUsage.setTotalTokens(total);
        tokenUsage.setCachedTokens(usage != null && usage.getCachedTokens() != null
                ? usage.getCachedTokens().longValue() : null);
        tokenUsage.setCreatedAt(System.currentTimeMillis());
        tokenUsageRepository.save(tokenUsage);
    }

    /**
     * 统计指定时间范围（可选模型）的Tokens报表
     */
    @Transactional(readOnly = true)
    public TokenStatsDTO stats(long start, long end, Long llmId) {
        List<TokenUsageStatsRow> all = tokenUsageRepository.findStatsRows(start, end);
        Map<Long, String> llmNameById = llmRepository.findAll().stream()
                .collect(Collectors.toMap(Llm::getId, Llm::getName, (a, b) -> a));

        List<TokenUsageStatsRow> scoped = (llmId == null) ? all
                : all.stream().filter(l -> llmId.equals(l.llmId())).toList();
        List<String> dates = buildDateList(start, end);

        return TokenStatsDTO.builder()
                .summary(buildSummary(scoped))
                .topModels(buildTopModels(all, llmNameById))
                .dates(dates)
                .modelSeries(buildModelSeries(scoped, dates, llmNameById))
                .build();
    }

    private TokenStatsSummary buildSummary(List<TokenUsageStatsRow> rows) {
        long input = 0, output = 0, cached = 0, cacheReportedInput = 0;
        boolean cacheReported = false;
        for (TokenUsageStatsRow l : rows) {
            input += l.inputTokens();
            output += l.outputTokens();
            if (l.cachedTokens() != null) {
                cacheReported = true;
                cached += l.cachedTokens();
                cacheReportedInput += l.inputTokens();
            }
        }
        return TokenStatsSummary.builder()
                .totalInputTokens(input)
                .totalOutputTokens(output)
                .totalTokens(input + output)
                .totalCachedTokens(cacheReported ? cached : null)
                .cacheHitRate(cacheReportedInput > 0 ? (double) cached / cacheReportedInput : null)
                .callCount(rows.size())
                .build();
    }

    private List<TokenTopModel> buildTopModels(List<TokenUsageStatsRow> all, Map<Long, String> llmNameById) {
        Map<Long, List<TokenUsageStatsRow>> grouped = groupByLlm(all);
        return grouped.entrySet().stream()
                .map(e -> toTopModel(e.getKey(), e.getValue(), llmNameById))
                .sorted(Comparator.comparingLong(TokenTopModel::getCallCount).reversed())
                .limit(3)
                .toList();
    }

    private List<String> buildDateList(long start, long end) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate first = Instant.ofEpochMilli(start).atZone(zone).toLocalDate();
        LocalDate last = Instant.ofEpochMilli(end).atZone(zone).toLocalDate();
        List<String> dates = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            dates.add(d.toString());
        }
        return dates;
    }

    private List<TokenModelSeries> buildModelSeries(List<TokenUsageStatsRow> scoped, List<String> dates,
                                                     Map<Long, String> llmNameById) {
        if (dates.isEmpty()) {
            return List.of();
        }
        ZoneId zone = ZoneId.systemDefault();
        Map<LocalDate, Integer> dateIndex = new HashMap<>();
        for (int i = 0; i < dates.size(); i++) {
            dateIndex.put(LocalDate.parse(dates.get(i)), i);
        }

        Map<Long, long[][]> accum = new LinkedHashMap<>();
        for (TokenUsageStatsRow l : scoped) {
            int idx = dateIndex.getOrDefault(
                    Instant.ofEpochMilli(l.createdAt()).atZone(zone).toLocalDate(), -1);
            if (idx < 0) {
                continue;
            }
            long[][] arr = accum.computeIfAbsent(groupKey(l.llmId()), k -> new long[4][dates.size()]);
            arr[0][idx] += l.inputTokens();
            arr[1][idx] += l.outputTokens();
            arr[2][idx] += l.totalTokens();
            arr[3][idx] += 1;
        }

        Map<Long, String> modelByLlm = scoped.stream()
                .filter(l -> l.model() != null)
                .collect(Collectors.toMap(l -> groupKey(l.llmId()), TokenUsageStatsRow::model, (a, b) -> a));

        return accum.entrySet().stream()
                .map(e -> {
                    long[][] arr = accum.get(e.getKey());
                    long totalSum = 0;
                    for (long v : arr[2]) {
                        totalSum += v;
                    }
                    String model = modelByLlm.get(e.getKey());
                    return new SeriesWithTotal(TokenModelSeries.builder()
                            .llmId(e.getKey())
                            .llmName(resolveName(e.getKey(), model, llmNameById))
                            .model(model)
                            .input(arr[0])
                            .output(arr[1])
                            .total(arr[2])
                            .callCount(arr[3])
                            .build(), totalSum);
                })
                .sorted(Comparator.comparingLong(SeriesWithTotal::total).reversed())
                .map(SeriesWithTotal::series)
                .toList();
    }

    private Map<Long, List<TokenUsageStatsRow>> groupByLlm(List<TokenUsageStatsRow> rows) {
        return rows.stream().collect(Collectors.groupingBy(
                l -> groupKey(l.llmId()), LinkedHashMap::new, Collectors.toList()));
    }

    private TokenTopModel toTopModel(Long llmId, List<TokenUsageStatsRow> rows, Map<Long, String> llmNameById) {
        long input = rows.stream().mapToLong(TokenUsageStatsRow::inputTokens).sum();
        long output = rows.stream().mapToLong(TokenUsageStatsRow::outputTokens).sum();
        String model = rows.get(0).model();
        return TokenTopModel.builder()
                .llmId(llmId)
                .llmName(resolveName(llmId, model, llmNameById))
                .model(model)
                .inputTokens(input)
                .outputTokens(output)
                .callCount(rows.size())
                .build();
    }

    private Long groupKey(Long llmId) {
        return llmId != null ? llmId : 0L;
    }

    private String resolveName(Long llmId, String model, Map<Long, String> llmNameById) {
        String name = llmNameById.get(llmId);
        if (name != null && !name.isBlank()) {
            return name;
        }
        return model != null ? model : "未知模型";
    }

    private record SeriesWithTotal(TokenModelSeries series, long total) {
    }
}
