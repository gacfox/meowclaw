package com.gacfox.meowclaw.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 排序融合工具
 */
public class RrfFusionUtil {
    private static final int K = 60;

    /**
     * 对多个有序结果列表做RRF融合，返回合并后的ID列表
     *
     * @param rankedLists 每个列表按相关度从高到低排列
     * @param topN        返回数量
     */
    public static List<Long> fuse(List<List<Long>> rankedLists, int topN) {
        Map<Long, Double> scores = new HashMap<>();
        for (List<Long> list : rankedLists) {
            if (list == null || list.isEmpty()) {
                continue;
            }
            for (int i = 0; i < list.size(); i++) {
                Long id = list.get(i);
                if (id == null) continue;
                double score = 1.0 / (K + i + 1);
                scores.merge(id, score, Double::sum);
            }
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .toList();
    }
}
