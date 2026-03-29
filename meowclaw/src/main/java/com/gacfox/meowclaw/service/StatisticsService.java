package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.DailyStatisticsDto;
import com.gacfox.meowclaw.dto.StatisticsOverviewDto;
import com.gacfox.meowclaw.entity.LlmConfig;
import com.gacfox.meowclaw.repository.LlmConfigRepository;
import com.gacfox.meowclaw.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatisticsService {
    private final MessageRepository messageRepository;
    private final LlmConfigRepository llmConfigRepository;

    public StatisticsService(MessageRepository messageRepository, LlmConfigRepository llmConfigRepository) {
        this.messageRepository = messageRepository;
        this.llmConfigRepository = llmConfigRepository;
    }

    public StatisticsOverviewDto getOverview(Long startTime, Long endTime) {
        StatisticsOverviewDto overview = new StatisticsOverviewDto();
        
        int modelCount = llmConfigRepository.findAll().size();
        overview.setModelCount(modelCount);

        long[] tokenStats = messageRepository.sumTokensByTimeRange(startTime, endTime);
        overview.setTotalInputTokens(tokenStats[0]);
        overview.setTotalOutputTokens(tokenStats[1]);

        long messageCount = messageRepository.countByTimeRange(startTime, endTime);
        overview.setTotalMessages(messageCount);

        return overview;
    }

    public List<DailyStatisticsDto> getDailyStatistics(Long startTime, Long endTime, String apiUrl, String model) {
        LocalDate startDate = Instant.ofEpochMilli(startTime).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = Instant.ofEpochMilli(endTime).atZone(ZoneId.systemDefault()).toLocalDate();
        
        List<String[]> apiUrlModelPairs = getApiUrlModelPairs(apiUrl, model);
        Map<String, String> displayNameMap = buildDisplayNameMap();
        List<DailyStatisticsDto> results = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            long dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

            for (String[] pair : apiUrlModelPairs) {
                String pairApiUrl = pair[0];
                String pairModel = pair[1];

                DailyStatisticsDto dto = new DailyStatisticsDto();
                dto.setDate(date.format(formatter));
                dto.setApiUrl(pairApiUrl);
                dto.setModel(pairModel);
                dto.setDisplayName(getDisplayName(displayNameMap, pairApiUrl, pairModel));

                long[] stats = messageRepository.sumDailyTokensByApiUrlAndModel(dayStart, dayEnd, pairApiUrl, pairModel);
                dto.setInputTokens(stats[0]);
                dto.setOutputTokens(stats[1]);
                dto.setMessageCount(stats[2]);

                results.add(dto);
            }
        }

        return results;
    }

    public List<Map<String, String>> getAvailableModels() {
        List<String[]> pairs = messageRepository.findDistinctApiUrlModelPairs();
        Map<String, String> displayNameMap = buildDisplayNameMap();
        List<Map<String, String>> result = new ArrayList<>();
        
        for (String[] pair : pairs) {
            String apiUrl = pair[0];
            String model = pair[1];
            Map<String, String> item = new HashMap<>();
            item.put("apiUrl", apiUrl);
            item.put("model", model);
            item.put("displayName", getDisplayName(displayNameMap, apiUrl, model));
            result.add(item);
        }
        
        return result;
    }

    private List<String[]> getApiUrlModelPairs(String apiUrl, String model) {
        if (apiUrl != null && !apiUrl.isEmpty() && model != null && !model.isEmpty()) {
            return Collections.singletonList(new String[]{apiUrl, model});
        }
        return messageRepository.findDistinctApiUrlModelPairs();
    }

    private Map<String, String> buildDisplayNameMap() {
        Map<String, String> map = new HashMap<>();
        List<LlmConfig> configs = llmConfigRepository.findAll();
        for (LlmConfig config : configs) {
            String key = config.getApiUrl() + "||" + config.getModel();
            map.put(key, config.getName());
        }
        return map;
    }

    private String getDisplayName(Map<String, String> displayNameMap, String apiUrl, String model) {
        String key = apiUrl + "||" + model;
        String name = displayNameMap.get(key);
        if (name != null) {
            return name;
        }
        return model;
    }
}
