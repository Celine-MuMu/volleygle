
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

        // 強制在查詢後加上「排球」，避免搜尋結果發散到不相關領域
        // 在執行 Google 搜尋前，直接把垃圾網域踢掉
        String combinedQuery = "\"" + keyword + "\"";
        // -site:twmp.com.tw -site:i-pass.com.tw -site:gov.tw
        if (!keyword.toLowerCase().contains("排球") && !keyword.toLowerCase().contains("volleyball")) {
            combinedQuery += " 排球";
        }
        System.out.println("【DEBUG】優化後實際查詢關鍵字: " + combinedQuery);

        Set<String> initialUrlSet = new HashSet<>();

        // 1. 使用 LinkedHashMap 保持插入順序，同時記錄 URL 及其原始名次
        // Key: URL, Value: Initial Rank
        Map<String, Integer> urlWithRankMap = new LinkedHashMap<>();

        // --- 步驟 A：加入手動種子 (優先權最高，名次設為 0) ---
        if (manualSeedUrls != null) {
            for (String url : manualSeedUrls) {
                urlWithRankMap.putIfAbsent(url, 0);
            }
        }

        // --- 步驟 B：取得 Google API 搜尋結果 (名次從 1 開始) ---
        Map<String, String> googleResults = googleApiGateway.search(combinedQuery);
        int rankCounter = 1;
        for (String url : googleResults.keySet()) { // 現在 Key 才是網址
            urlWithRankMap.putIfAbsent(url, rankCounter++);
        }

        // --- 步驟 C：執行社群媒體搜尋 (名次延續下去，或給予特定的起始名次) ---
        String socialQuery = combinedQuery + " (site:instagram.com OR site:threads.net OR site:dcard.tw)";
        Map<String, String> socialResults = googleApiGateway.search(socialQuery);
        for (String url : socialResults.keySet()) {
            urlWithRankMap.putIfAbsent(url, rankCounter++);
        }

        if (urlWithRankMap.isEmpty()) {
            System.out.println("沒有任何起始 URL，流程中止。");
            return new ArrayList<>();
        }

        // --- 【新增：觀察 Google 原始排名】 ---
        System.out.println("\n=== [DEBUG] Google API 原始回傳名次清單 ===");
        urlWithRankMap.forEach((url, rank) -> {
            String source = (rank == 0) ? "[手動種子]" : "[Google搜尋]";
            System.out.println(String.format("%-10s 名次: %2d | 網址: %s", source, rank, url));
        });
        System.out.println("==========================================\n");

        System.out.println("總共找到 " + urlWithRankMap.size() + " 個起始 URL。開始並行建樹...");

        // 2. 並行建構 WebNode 樹 (將 rank 傳入 linkExtractor)
        // 修改後的 map 區塊
        List<CompletableFuture<WebNode>> futures = urlWithRankMap.entrySet().stream()
                .map(entry -> {
                    String url = entry.getKey();
                    int initialRank = entry.getValue();

                    // 明確宣告類型，幫助編譯器
                    CompletableFuture<WebNode> future = CompletableFuture.supplyAsync(() -> {
                        return linkExtractor.buildWebTree(url, keyword, initialRank);
                    }, executorService)
                            .orTimeout(30, TimeUnit.SECONDS)
                            .exceptionally(ex -> {
                                System.err.println(" [Async Task] 警告: URL 處理失敗: " + url);
                                return null;
                            });

                    return future;
                })
                .collect(Collectors.toList());

        // 等待所有任務完成
        List<WebNode> rootNodes = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))
                .join();

        // 3. 排名和輸出
        return rankingServer.rankWebTree(rootNodes);
    }
}
