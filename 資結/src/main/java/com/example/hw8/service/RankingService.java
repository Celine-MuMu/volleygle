/*package com.example.hw8.service;

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
     **/
   /*  public Map<String, String> searchAndRank(String query) {
        //String lowerQuery = query.toLowerCase(); > 移除，直接使用原始 query。因為已在 GoogleApiGateway 中已經使用 URLEncoder 處理了編碼。

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
}*/

package com.example.hw8.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class RankingService {

    private final GoogleApiGateway apiGateway;
    private final KeywordScorer keywordScorer;

    // 建立一個固定大小的執行緒池，專門用於爬蟲任務
    // 限制同時發出的請求數量，避免資源耗盡或被網站封鎖
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public RankingService(GoogleApiGateway apiGateway, KeywordScorer keywordScorer) {
        this.apiGateway = apiGateway;
        this.keywordScorer = keywordScorer;
    }

    /**
     * 執行完整的流程：呼叫 API -> 多執行緒計分 -> 排序。
     */
    public Map<String, String> searchAndRank(String query) {
        // 1. 呼叫 Gateway 獲取未排序的原始結果 (這是同步的 API 請求)
        Map<String, String> results = apiGateway.search(query);

        if (results.isEmpty()) {
            return new LinkedHashMap<>(); 
        }

        System.out.println("[Ranking Service] 獲取原始結果 " + results.size() + " 筆，開始並行計分排序...");
        
        // 2. **【核心變動】** 使用 CompletableFuture 進行並行計分
        // 這裡明確指定使用 AbstractMap.SimpleEntry 來避免編譯器錯誤
        List<CompletableFuture<AbstractMap.SimpleEntry<String, Integer>>> futures = results.entrySet().stream()
                .map(entry -> {
                    String title = entry.getKey();
                    String url = entry.getValue();

                    // 將耗時的 getPageScore 放入執行緒池中異步執行 (加速關鍵)
                    return CompletableFuture.supplyAsync(() -> {
                        int score = keywordScorer.getPageScore(url, query);
                        
                        // 返回一個包含 Title 和 Score 的 Entry
                        return new AbstractMap.SimpleEntry<>(title, score);
                    }, executor)
                    // 處理例外：如果執行緒中發生錯誤，給予 0 分，確保流程不中斷
                    .exceptionally(ex -> {
                        // 當發生錯誤時，將錯誤印出以便除錯
                        System.err.println("[Ranking Service] 處理網址失敗: " + url + " | 錯誤: " + ex.getMessage());
                        return new AbstractMap.SimpleEntry<>(title, 0);
                    });
                })
                .collect(Collectors.toList());

        // 3. 等待所有異步任務完成
        // 使用 join() 取得所有已完成的結果 (Title, Score)
        List<AbstractMap.SimpleEntry<String, Integer>> scoredEntries = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // 4. 根據分數進行排序
        Map<String, String> rankedResults = scoredEntries.stream()
                // 依據分數 (Value) 降冪排序 (分數高的排前面)
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,     // Title 作為 Key
                        e -> results.get(e.getKey()), // 從原始 results Map 取回 Link 作為 Value
                        (e1, e2) -> e1,
                        LinkedHashMap::new)); // 使用 LinkedHashMap 保持排序後的順序

        return rankedResults;
    }
}