package com.example.hw8.server;

import com.example.hw8.model.WebNode;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SearchManager { // 專門負責協調所有服務

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
        // Set<String> initialUrlSet =
        // googleApiGateway.search(keyword).values().stream().collect(Collectors.toSet());
        Set<String> initialUrlSet = new HashSet<>();

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
        List<WebNode> rootNodes = new ArrayList<>();

        for (String url : combinedUrls) {
            System.out.println("開始建構樹: " + url);
            WebNode root = linkExtractor.buildWebTree(url, keyword);
            if (root != null) {
                rootNodes.add(root);
            }
        }

        // 3. 排名和輸出 (RankingServer 處理總分計算和排序)
        if (rootNodes.isEmpty()) {
            return new ArrayList<>();
        }

        // 呼叫 RankingServer 的排名方法
        return rankingServer.rankWebTree(rootNodes);
    }
}