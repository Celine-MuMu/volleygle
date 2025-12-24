
//         return rankingServer.rankWebTree(rootNodes);
//     }
// }
package com.example.hw8.server;

import com.example.hw8.model.WebNode;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class SearchManager {

    // 設置用於 I/O 任務的執行緒池
    private final ExecutorService executorService = Executors.newFixedThreadPool(20);

    // 注入服務
    private final GoogleApiGateway googleApiGateway;
    private final LinkExtractor linkExtractor;
    private final RankingServer rankingServer;

    public SearchManager(GoogleApiGateway googleApiGateway,
            LinkExtractor linkExtractor,
            RankingServer rankingServer) {
        this.googleApiGateway = googleApiGateway;
        this.linkExtractor = linkExtractor;
        this.rankingServer = rankingServer;
    }

    /**
     * 執行完整的樹狀搜尋和排名流程。
     */

    public List<WebNode> performTreeSearchAndRank(String keyword, List<String> manualSeedUrls) {
        System.out.println("--- SearchManager 啟動樹狀搜尋流程: " + keyword + " ---");

        // 1. 處理關鍵字邏輯
        String combinedQuery = "\"" + keyword + "\"";
        if (!keyword.toLowerCase().contains("排球") && !keyword.toLowerCase().contains("volleyball")) {
            combinedQuery += " 排球";
        }

        // 🏆 修正點 1：建立一個統一儲存「所有」來源標題的 Map
        Map<String, String> allTitles = new HashMap<>();
        Map<String, Integer> urlWithRankMap = new LinkedHashMap<>();

        // 步驟 A：手動種子
        if (manualSeedUrls != null) {
            for (String url : manualSeedUrls) {
                urlWithRankMap.putIfAbsent(url, 0);
                allTitles.put(url, "手動種子網頁");
            }
        }

        // 步驟 B：一般搜尋
        Map<String, String> googleResults = googleApiGateway.search(combinedQuery);
        int rankCounter = 1;
        for (Map.Entry<String, String> entry : googleResults.entrySet()) {
            String url = entry.getKey();
            String title = entry.getValue();
            urlWithRankMap.putIfAbsent(url, rankCounter++);
            allTitles.put(url, title);
        }

        // 步驟 C：社群搜尋
        String socialQuery = combinedQuery + " (site:instagram.com OR site:threads.net OR site:dcard.tw)";
        Map<String, String> socialResults = googleApiGateway.search(socialQuery);
        for (Map.Entry<String, String> entry : socialResults.entrySet()) {
            String url = entry.getKey();
            String title = entry.getValue();
            urlWithRankMap.putIfAbsent(url, rankCounter++);
            allTitles.put(url, title);
        }

        if (urlWithRankMap.isEmpty())
            return new ArrayList<>();

        // 2. 並行建構 WebNode 樹
        // 🏆 修正點 2：在 entry 前面強制加上類型 (Map.Entry<String, Integer> entry)
        List<CompletableFuture<WebNode>> futures = urlWithRankMap.entrySet().stream()
                .map((Map.Entry<String, Integer> entry) -> {
                    String url = entry.getKey();
                    int initialRank = entry.getValue();

                    // 🏆 從統一標題 Map 拿資料，確保社群連結也有標題
                    String apiTitle = allTitles.getOrDefault(url, "未知名稱");

                    // 🏆 修正點 3：顯式指定 supplyAsync 的回傳類型為 <WebNode>
                    return CompletableFuture.<WebNode>supplyAsync(() -> {
                        return linkExtractor.buildWebTree(url, keyword, initialRank, apiTitle);
                    }, executorService)
                            .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .exceptionally(ex -> {
                                System.err.println(" [Async Task] 警告: URL 處理失敗: " + url);
                                return null;
                            });
                })
                .collect(java.util.stream.Collectors.toList());

        // 3. 收集結果並排名
        List<WebNode> rootNodes = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toList()))
                .join();

        return rankingServer.rankWebTree(rootNodes);
    }
    // public List<WebNode> performTreeSearchAndRank(String keyword, List<String>
    // manualSeedUrls) {
    // System.out.println("--- SearchManager 啟動樹狀搜尋流程: " + keyword + " ---");

    // // 關鍵字強制加上「排球」
    // String combinedQuery = "\"" + keyword + "\"";
    // // -site:twmp.com.tw -site:i-pass.com.tw -site:gov.tw
    // if (!keyword.toLowerCase().contains("排球") &&
    // !keyword.toLowerCase().contains("volleyball")) {
    // combinedQuery += " 排球";
    // }
    // System.out.println("【DEBUG】優化後實際查詢關鍵字: " + combinedQuery);

    // // 1. 使用 LinkedHashMap 保持插入順序，同時記錄 URL 及其原始名次
    // // Key: URL, Value: Initial Rank
    // Map<String, Integer> urlWithRankMap = new LinkedHashMap<>();

    // // 步驟 A：加入手動種子 (優先權最高，名次設為 0) ---
    // if (manualSeedUrls != null) {
    // for (String url : manualSeedUrls) {
    // urlWithRankMap.putIfAbsent(url, 0);
    // }
    // }

    // // 步驟 B：取得 Google API 搜尋結果 (名次從 1 開始) ---
    // Map<String, String> googleResults = googleApiGateway.search(combinedQuery);
    // int rankCounter = 1;
    // for (String url : googleResults.keySet()) { // 現在 Key 才是網址
    // urlWithRankMap.putIfAbsent(url, rankCounter++);
    // }

    // // 步驟 C：執行社群媒體搜尋 (名次延續下去，或給予特定的起始名次) ---
    // String socialQuery = combinedQuery + " (site:instagram.com OR
    // site:threads.net OR site:dcard.tw)";
    // Map<String, String> socialResults = googleApiGateway.search(socialQuery);
    // for (String url : socialResults.keySet()) {
    // urlWithRankMap.putIfAbsent(url, rankCounter++);
    // }

    // if (urlWithRankMap.isEmpty()) {
    // System.out.println("沒有任何起始 URL，流程中止。");
    // return new ArrayList<>();
    // }

    // // --- 觀察 Google 原始排名 ---
    // System.out.println("\n=== [DEBUG] Google API 原始回傳名次清單 ===");
    // urlWithRankMap.forEach((url, rank) -> {
    // String source = (rank == 0) ? "[手動種子]" : "[Google搜尋]";
    // System.out.println(String.format("%-10s 名次: %2d | 網址: %s", source, rank,
    // url));
    // });
    // System.out.println("==========================================\n");

    // System.out.println("總共找到 " + urlWithRankMap.size() + " 個起始 URL。開始並行建樹...");

    // // 2. 並行建構 WebNode 樹 (將 rank 傳入 linkExtractor)
    // List<CompletableFuture<WebNode>> futures = urlWithRankMap.entrySet().stream()
    // .map(entry -> {
    // String url = entry.getKey();
    // int initialRank = entry.getValue();
    // String apiTitle = googleResults.get(url);
    // if (apiTitle == null)
    // apiTitle = "未知名稱";

    // // 明確宣告類型，幫助編譯器
    // CompletableFuture<WebNode> future = CompletableFuture.supplyAsync(() -> {
    // return linkExtractor.buildWebTree(url, keyword, initialRank, apiTitle);
    // }, executorService)
    // .orTimeout(30, TimeUnit.SECONDS)
    // .exceptionally(ex -> {
    // System.err.println(" [Async Task] 警告: URL 處理失敗: " + url);
    // return null;
    // });

    // return future;
    // })
    // .collect(Collectors.toList());

    // // 等待所有任務完成
    // List<WebNode> rootNodes = CompletableFuture.allOf(futures.toArray(new
    // CompletableFuture[0]))
    // .thenApply(v -> futures.stream()
    // .map(CompletableFuture::join)
    // .filter(Objects::nonNull)
    // .collect(Collectors.toList()))
    // .join();

    // // 3. 排名和輸出
    // return rankingServer.rankWebTree(rootNodes);
    // }
}
