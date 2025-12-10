package com.example.hw8.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.net.URL; // 新增：用於提取 Domain

@Service
public class RankingService {

    private final GoogleApiGateway apiGateway;
    private final KeywordScorer keywordScorer;
    private final KeywordAnalyzer keywordAnalyzer; // 【新增】Stage 4 依賴

    // 建立一個固定大小的執行緒池
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    // 【建構子修改】必須加入 KeywordAnalyzer
    public RankingService(GoogleApiGateway apiGateway, KeywordScorer keywordScorer, KeywordAnalyzer keywordAnalyzer) {
        this.apiGateway = apiGateway;
        this.keywordScorer = keywordScorer;
        this.keywordAnalyzer = keywordAnalyzer; // 【初始化】確保 final 欄位被初始化
    }

    /**
     * 輔助方法：從 URL 提取網域 (Stage 2 輔助)
     */
    private String getDomain(String url) {
        try {
            // 使用 java.net.URL 進行更標準的提取
            URL u = new URL(url);
            String domain = u.getHost().replaceAll("^(www\\.)", "");
            return domain.toLowerCase();
        } catch (Exception e) {
            // 如果 URL 無效，給予預設網域避免崩潰
            return "unknown_domain";
        }
    }

    /**
     * 執行完整的流程：呼叫 API -> 多執行緒計分 -> 網站與網頁排序 (Stage 2 & 3)
     */
    public Map<String, String> searchAndRank(String query) {
        Map<String, String> results = apiGateway.search(query);
        if (results.isEmpty()) return new LinkedHashMap<>();

        System.out.println("[Ranking Service] 獲取原始結果 " + results.size() + " 筆，開始並行計分排序...");

        // 1. 【核心】並行計分
        List<CompletableFuture<AbstractMap.SimpleEntry<String, Integer>>> futures = results.entrySet().stream()
                .map(entry -> CompletableFuture.supplyAsync(() -> {
                    int score = keywordScorer.getPageScore(entry.getValue(), query);
                    return new AbstractMap.SimpleEntry<>(entry.getKey(), score);
                }, executor)
                .exceptionally(ex -> {
                    System.err.println("[Ranking Service] 處理網址失敗: " + entry.getValue() + " | 錯誤: " + ex.getMessage());
                    return new AbstractMap.SimpleEntry<>(entry.getKey(), 0);
                }))
                .collect(Collectors.toList());

        List<AbstractMap.SimpleEntry<String, Integer>> scoredEntries = futures.stream()
                .map(CompletableFuture::join) // 【解決 'futures cannot be resolved'】
                .collect(Collectors.toList());

        // 2. 【Stage 2】網站分組與平均計分
        Map<String, List<AbstractMap.SimpleEntry<String, Integer>>> groupedByDomain = scoredEntries.stream()
                .collect(Collectors.groupingBy(entry -> getDomain(results.get(entry.getKey()))));

        Map<String, Double> domainScores = groupedByDomain.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().stream().mapToInt(AbstractMap.SimpleEntry::getValue).average().orElse(0.0)
                ));

        // 3. 【Stage 2】結合網頁分數與網域分數排序
        Map<String, String> finalRankedResults = new LinkedHashMap<>();
        Set<String> addedDomains = new HashSet<>(); 

        domainScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder())) 
                .forEach(domainEntry -> {
                    String domain = domainEntry.getKey();
                    
                    List<AbstractMap.SimpleEntry<String, Integer>> pagesInDomain = groupedByDomain.get(domain).stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                            .collect(Collectors.toList());

                    // 控制單一網域的結果數量
                    int limit = (addedDomains.size() < 5) ? 3 : 1;
                    
                    pagesInDomain.stream().limit(limit).forEach(page -> {
                        finalRankedResults.put(page.getKey(), results.get(page.getKey()));
                    });
                    addedDomains.add(domain);
                });

        return finalRankedResults;
    }

    /**
     * 推導相關關鍵字 (Stage 4)
     */
    public List<String> getRelativeKeywords(String query, Map<String, String> topResults) {
        String analysisText = topResults.entrySet().stream()
                .limit(10) // 只分析前 10 筆結果的標題/連結
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(" "));

        return keywordAnalyzer.getRelevantKeywords(analysisText, query, 5); 
    }
}