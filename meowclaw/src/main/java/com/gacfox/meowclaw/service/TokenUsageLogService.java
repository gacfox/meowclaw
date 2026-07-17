package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.TokenStatsDTO;
import com.gacfox.meowclaw.dto.TokenStatsDTO.TokenModelSeries;
import com.gacfox.meowclaw.dto.TokenStatsDTO.TokenStatsSummary;
import com.gacfox.meowclaw.dto.TokenStatsDTO.TokenTopModel;
import com.gacfox.meowclaw.entity.Llm;
import com.gacfox.meowclaw.entity.TokenUsageLog;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageContext;
import com.gacfox.meowclaw.repository.LlmRepository;
import com.gacfox.meowclaw.repository.TokenUsageLogRepository;
import com.gacfox.proarc.agentic.model.openai.Usage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TokenUsageLogService {

    private final TokenUsageLogRepository tokenUsageLogRepository;
    private final LlmRepository llmRepository;

    public TokenUsageLogService(TokenUsageLogRepository tokenUsageLogRepository, LlmRepository llmRepository) {
        this.tokenUsageLogRepository = tokenUsageLogRepository;
        this.llmRepository = llmRepository;
    }

    /**
     * 记录一次LLM调用的Tokens消耗明细
     */
    @Transactional
    public void record(TokenUsageContext ctx, Usage usage) {
        long input = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0L;
        long output = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0L;
        long total = usage.getTotalTokens() != null ? usage.getTotalTokens() : input + output;

        TokenUsageLog log = new TokenUsageLog();
        log.setLlmId(ctx.llmId());
        log.setAgentId(ctx.agentId());
        log.setConversationId(ctx.conversationId());
        log.setBatchId(ctx.batchId());
        log.setModel(ctx.model());
        log.setInputTokens(input);
        log.setOutputTokens(output);
        log.setTotalTokens(total);
        log.setCreatedAt(System.currentTimeMillis());
        tokenUsageLogRepository.save(log);
    }

    /**
     * 统计指定时间范围（可选模型）的Tokens报表
     *
     * @param start 起始时间戳毫秒（含）
     * @param end   结束时间戳毫秒（含）
     * @param llmId 模型ID，null表示全部模型
     */
    @Transactional(readOnly = true)
    public TokenStatsDTO stats(long start, long end, Long llmId) {
        List<TokenUsageLog> all = tokenUsageLogRepository.findByCreatedAtBetween(start, end);
        Map<Long, String> llmNameById = llmRepository.findAll().stream()
                .collect(Collectors.toMap(Llm::getId, Llm::getName, (a, b) -> a));

        List<TokenUsageLog> scoped = (llmId == null) ? all
                : all.stream().filter(l -> llmId.equals(l.getLlmId())).toList();

        List<String> dates = buildDateList(start, end);

        return TokenStatsDTO.builder()
                .summary(buildSummary(scoped))
                .topModels(buildTopModels(all, llmNameById))
                .dates(dates)
                .modelSeries(buildModelSeries(scoped, dates, llmNameById))
                .build();
    }

    private TokenStatsSummary buildSummary(List<TokenUsageLog> rows) {
        long input = 0, output = 0;
        for (TokenUsageLog l : rows) {
            input += l.getInputTokens();
            output += l.getOutputTokens();
        }
        return TokenStatsSummary.builder()
                .totalInputTokens(input)
                .totalOutputTokens(output)
                .totalTokens(input + output)
                .callCount(rows.size())
                .build();
    }

    private List<TokenTopModel> buildTopModels(List<TokenUsageLog> all, Map<Long, String> llmNameById) {
        Map<Long, List<TokenUsageLog>> grouped = groupByLlm(all);
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

    private List<TokenModelSeries> buildModelSeries(List<TokenUsageLog> scoped, List<String> dates,
                                                     Map<Long, String> llmNameById) {
        if (dates.isEmpty()) {
            return List.of();
        }
        ZoneId zone = ZoneId.systemDefault();
        Map<LocalDate, Integer> dateIndex = new HashMap<>();
        for (int i = 0; i < dates.size(); i++) {
            dateIndex.put(LocalDate.parse(dates.get(i)), i);
        }

        // key=llmId, value=按日累加的[输入,输出,合计,调用量]
        Map<Long, long[][]> accum = new LinkedHashMap<>();
        for (TokenUsageLog l : scoped) {
            int idx = dateIndex.getOrDefault(
                    Instant.ofEpochMilli(l.getCreatedAt()).atZone(zone).toLocalDate(), -1);
            if (idx < 0) {
                continue;
            }
            long[][] arr = accum.computeIfAbsent(groupKey(l.getLlmId()), k -> new long[4][dates.size()]);
            arr[0][idx] += l.getInputTokens();
            arr[1][idx] += l.getOutputTokens();
            arr[2][idx] += l.getTotalTokens();
            arr[3][idx] += 1;
        }

        Map<Long, String> modelByLlm = scoped.stream()
                .filter(l -> l.getModel() != null)
                .collect(Collectors.toMap(l -> groupKey(l.getLlmId()), TokenUsageLog::getModel, (a, b) -> a));

        return accum.entrySet().stream()
                .map(e -> {
                    long[][] arr = e.getValue();
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

    private Map<Long, List<TokenUsageLog>> groupByLlm(List<TokenUsageLog> rows) {
        return rows.stream().collect(Collectors.groupingBy(
                l -> groupKey(l.getLlmId()), LinkedHashMap::new, Collectors.toList()));
    }

    private TokenTopModel toTopModel(Long llmId, List<TokenUsageLog> rows, Map<Long, String> llmNameById) {
        long input = rows.stream().mapToLong(TokenUsageLog::getInputTokens).sum();
        long output = rows.stream().mapToLong(TokenUsageLog::getOutputTokens).sum();
        String model = rows.get(0).getModel();
        return TokenTopModel.builder()
                .llmId(llmId)
                .llmName(resolveName(llmId, model, llmNameById))
                .model(model)
                .callCount(rows.size())
                .inputTokens(input)
                .outputTokens(output)
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
