package com.example.hw8.server;

import com.example.hw8.model.WebNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RankingServer {

    /**
     * 核心排名邏輯
     */
    public List<WebNode> rankWebTree(List<WebNode> rootNodes) {
        if (rootNodes == null || rootNodes.isEmpty())
            return rootNodes;

        // 1. 先計算每棵樹的總分 (TotalScore)
        rootNodes.forEach(this::calculateTotalScore);

        // 2. 執行過濾與排序 (根節點排序)
        List<WebNode> filteredNodes = rootNodes.stream()
                // .filter(node -> node.getTotalScore() > 0)
                .sorted(Comparator.comparingInt(WebNode::getTotalScore).reversed())
                .collect(Collectors.toCollection(ArrayList::new));

        // 3. 子網頁也照分數排序
        filteredNodes.forEach(this::sortChildrenRecursive);

        System.out.println("[Ranking Server] 排序完成。剩餘有效網站數量: " + filteredNodes.size());
        return filteredNodes;
    }

    private void sortChildrenRecursive(WebNode node) {
        if (node == null || node.getChildren().isEmpty())
            return;

        // 子節點依據該頁面自己的 score (關鍵字出現頻率) 排序
        node.getChildren().sort(Comparator.comparing(WebNode::getScore).reversed());

        // 繼續對下一層子節點進行排序
        for (WebNode child : node.getChildren()) {
            sortChildrenRecursive(child);
        }
    }

    public int calculateTotalScore(WebNode node) {
        if (node == null)
            return 0;
        int childTotalScore = 0;
        for (WebNode child : node.getChildren()) {
            childTotalScore += calculateTotalScore(child);
        }
        int totalScore = node.getScore() + childTotalScore;
        node.setTotalScore(totalScore);
        return totalScore;
    }
}
