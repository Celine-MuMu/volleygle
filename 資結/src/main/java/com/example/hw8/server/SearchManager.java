package com.example.hw8.server;

import com.example.hw8.model.WebNode;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture; // 引入異步處理的核心類
import java.util.concurrent.ExecutorService; // 引入執行緒池
import java.util.concurrent.Executors; // 引入執行緒池工具
import java.util.stream.Collectors;

@Service
public class SearchManager { // 專門負責協調所有服務

    // 設置一個專門用於 I/O 密集型任務的執行緒池
    // 初始 URL 數量通常不多，可以設定一個適中的數量，例如 20 個
    private final ExecutorService executorService = Executors.newFixedThreadPool(20);

    // 注入所有被協調的服務
    private final GoogleApiGateway googleApiGateway;
    private final LinkExtractor linkExtractor;
    private final RankingServer rankingServer;

    // 建構子注入
    public SearchManager(GoogleApiGateway googleApiGateway,
            LinkExtractor linkExtractor,
            RankingServer rankingServer) {
        this.googleApiGateway = googleApiGateway;
        this.linkExtractor = linkExtractor;
        this.rankingServer = rankingServer;
    }

    /**
     * 執行完整的樹狀搜尋和排名流程 (整合所有服務)。
     * 
     * @param keyword        使用者輸入的關鍵字。
     * @param manualSeedUrls 手動塞入的 URL 列表
     * @return 排序好的 WebNode 根節點列表。
     */
    public List<WebNode> performTreeSearchAndRank(String keyword, List<String> manualSeedUrls) {
        System.out.println("--- SearchManager 啟動樹狀搜尋流程: " + keyword + " ---");

        // 1. 取得初始 URL 列表 (Google API 結果 + 手動種子)
        // 假設 apiGateway.search 返回 Map<Title, URL>
        Set<String> initialUrlSet = googleApiGateway.search(keyword).values().stream().collect(Collectors.toSet());
        // Set<String> initialUrlSet = new HashSet<>();

        // 合併手動塞入的網址
        if (manualSeedUrls != null) {
            initialUrlSet.addAll(manualSeedUrls);
        }
        if (initialUrlSet.isEmpty()) {
            System.out.println("沒有任何起始 URL，流程中止。");
            return new ArrayList<>();
        }

        List<String> combinedUrls = new ArrayList<>(initialUrlSet);
        System.out.println("總共找到 " + combinedUrls.size() + " 個起始 URL。開始並行建樹...");

        // 2. 建構 WebNode 樹 (LinkExtractor 處理爬取、建樹、單頁計分)
        // 2a. 將每個 URL 的建樹任務轉換為一個 CompletableFuture (異步任務)
        List<CompletableFuture<WebNode>> futures = combinedUrls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> {
                    // 這個 Lambda 運算式會在執行緒池中執行
                    System.out.println("  [Async Task] 開始建構樹: " + url);
                    return linkExtractor.buildWebTree(url, keyword);
                }, executorService)) // 使用我們定義的執行緒池
                .collect(Collectors.toList());

        // 2b. 等待所有異步任務完成
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        // 2c. 收集結果並過濾掉 null
        List<WebNode> rootNodes = allOf.thenApply(v -> futures.stream()
                .map(CompletableFuture::join) // 取得每個 Future 的結果 (會阻塞直到結果可用)
                .filter(root -> root != null)
                .collect(Collectors.toList()))
                .join(); // 阻塞主執行緒直到所有結果都收集完畢

        System.out.println("並行建樹完成。成功建構 " + rootNodes.size() + " 個 WebNode 樹。");
        // List<WebNode> rootNodes = new ArrayList<>();

        // for (String url : combinedUrls) {
        // System.out.println("開始建構樹: " + url);
        // WebNode root = linkExtractor.buildWebTree(url, keyword);
        // if (root != null) {
        // rootNodes.add(root);
        // }
        // }

        // 3. 排名和輸出 (RankingServer 處理總分計算和排序)
        if (rootNodes.isEmpty()) {
            return new ArrayList<>();
        }

        // 呼叫 RankingServer 的排名方法
        return rankingServer.rankWebTree(rootNodes);
    }
}