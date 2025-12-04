package com.example.hw8.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RankingService {

    // 注入拆分後的兩個服務
    private final GoogleApiGateway apiGateway;
    private final KeywordScorer keywordScorer;

    // 透過建構子注入
    public RankingService(GoogleApiGateway apiGateway, KeywordScorer keywordScorer) {
        this.apiGateway = apiGateway;
        this.keywordScorer = keywordScorer;
    }

    /**
     * 執行完整的流程：呼叫 API -> 計分 -> 排序。
     * 回傳型別維持 Map<String, String>，保持與您舊程式碼的結果一致。
     */
    public Map<String, String> searchAndRank(String query) {
        String lowerQuery = query.toLowerCase();

        // 1. 呼叫 Gateway 獲取未排序的原始結果
        Map<String, String> results = apiGateway.search(query);

        System.out.println("[Ranking Service] 獲取原始結果 " + results.size() + " 筆，開始計分排序...");

        // 2. 執行排序邏輯 (這是您舊服務中的核心迴圈)
        Map<String, String> rankedResults = results.entrySet().stream()
                .sorted((e1, e2) -> {
                    String url1 = e1.getValue();
                    String url2 = e2.getValue();

                    // 呼叫 Scorer 服務來獲取分數
                    int score1 = keywordScorer.getPageScore(url1, lowerQuery);
                    int score2 = keywordScorer.getPageScore(url2, lowerQuery);

                    // 分數高的排前面
                    return Integer.compare(score2, score1);
                })
                // 使用 LinkedHashMap 來保持排序後的順序
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));

        return rankedResults;
    }
}