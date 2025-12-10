package com.example.hw8.server;

import com.example.hw8.model.WebNode;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;

@Service
public class RankingServer {

    public RankingServer() {
        // 現在只需要一個空的建構子
    }

    /**
     * 【核心邏輯】遞迴計算每個 WebNode 的總分 (Total Score)。
     * 這是實現「子網頁分數回饋」的核心。
     */
    public int calculateTotalScore(WebNode node) {
        if (node == null) {
            return 0;
        }

        int childTotalScore = 0;
        // 遞迴計算所有子節點的總分
        for (WebNode child : node.getChildren()) {
            childTotalScore += calculateTotalScore(child);
        }

        // 該節點的總分 = 自己的分數 (score) + 所有子節點的總分
        int totalScore = node.getScore() + childTotalScore;

        node.setTotalScore(totalScore);
        return totalScore;
    }

    /**
     * 對 WebNode 樹的根節點列表進行排名 (根據 TotalScore 降序)。
     * * @param rootNodes 尚未計算總分的 WebNode 根節點列表
     * 
     * @return 根據總分降序排序後的根節點列表
     */
    public List<WebNode> rankWebTree(List<WebNode> rootNodes) {
        System.out.println("[Ranking Server] 開始計算總分並排序...");

        // 1. 對每個樹的根節點呼叫遞迴方法，計算整個樹的總分
        rootNodes.forEach(this::calculateTotalScore);

        // 2. 根據 totalScore 進行降序排列
        rootNodes.sort(Comparator.comparing(WebNode::getTotalScore).reversed());

        System.out.println("[Ranking Server] 排序完成。");
        return rootNodes;
    }

    // 【刪除】移除 searchAndRank 方法，該流程將移至 SearchManager
}
// package com.example.hw8.service;

// import org.springframework.stereotype.Service;
// import java.util.*;
// import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;
// import java.util.stream.Collectors;

// @Service
// public class RankingService {

// private final GoogleApiGateway apiGateway;
// private final KeywordScorer keywordScorer;

// // 建立一個固定大小的執行緒池，專門用於爬蟲任務
// // 限制同時發出的請求數量，避免資源耗盡或被網站封鎖
// private final ExecutorService executor = Executors.newFixedThreadPool(10);

// public RankingService(GoogleApiGateway apiGateway, KeywordScorer
// keywordScorer) {
// this.apiGateway = apiGateway;
// this.keywordScorer = keywordScorer;
// }

// /**
// * 執行完整的流程：呼叫 API -> 多執行緒計分 -> 排序。
// */
// public Map<String, String> searchAndRank(String query) {
// // 1. 呼叫 Gateway 獲取未排序的原始結果 (這是同步的 API 請求)
// Map<String, String> results = apiGateway.search(query);

// if (results.isEmpty()) {
// return new LinkedHashMap<>();
// }

// System.out.println("[Ranking Service] 獲取原始結果 " + results.size() + "
// 筆，開始並行計分排序...");

// // 2. **【核心變動】** 使用 CompletableFuture 進行並行計分
// // 這裡明確指定使用 AbstractMap.SimpleEntry 來避免編譯器錯誤
// List<CompletableFuture<AbstractMap.SimpleEntry<String, Integer>>> futures =
// results.entrySet().stream()
// .map(entry -> {
// String title = entry.getKey();
// String url = entry.getValue();

// // 將耗時的 getPageScore 放入執行緒池中異步執行 (加速關鍵)
// return CompletableFuture.supplyAsync(() -> {
// int score = keywordScorer.getPageScore(url, query);

// // 返回一個包含 Title 和 Score 的 Entry
// return new AbstractMap.SimpleEntry<>(title, score);
// }, executor)
// // 處理例外：如果執行緒中發生錯誤，給予 0 分，確保流程不中斷
// .exceptionally(ex -> {
// // 當發生錯誤時，將錯誤印出以便除錯
// System.err.println("[Ranking Service] 處理網址失敗: " + url + " | 錯誤: " +
// ex.getMessage());
// return new AbstractMap.SimpleEntry<>(title, 0);
// });
// })
// .collect(Collectors.toList());

// // 3. 等待所有異步任務完成
// // 使用 join() 取得所有已完成的結果 (Title, Score)
// List<AbstractMap.SimpleEntry<String, Integer>> scoredEntries =
// futures.stream()
// .map(CompletableFuture::join)
// .collect(Collectors.toList());

// // 4. 根據分數進行排序
// Map<String, String> rankedResults = scoredEntries.stream()
// // 依據分數 (Value) 降冪排序 (分數高的排前面)
// .sorted(Map.Entry.<String,
// Integer>comparingByValue(Comparator.reverseOrder()))
// .collect(Collectors.toMap(
// Map.Entry::getKey, // Title 作為 Key
// e -> results.get(e.getKey()), // 從原始 results Map 取回 Link 作為 Value
// (e1, e2) -> e1,
// LinkedHashMap::new)); // 使用 LinkedHashMap 保持排序後的順序

// return rankedResults;
// }
// }