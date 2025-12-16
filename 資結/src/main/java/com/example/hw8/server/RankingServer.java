package com.example.hw8.server;

import com.example.hw8.model.WebNode;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;

@Service
public class RankingServer {

    public RankingServer() {
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

    // 遞迴排序所有子節點
    private void sortChildrenRecursive(WebNode node) {
        if (node == null) {
            return;
        }

        // 1. 對當前節點的子列表，根據它們的**單頁分數 (score)** 降冪排序
        // 由於子網頁在前端只顯示自身的 score，所以我們用 score 排序即可。
        node.getChildren().sort(Comparator.comparing(WebNode::getScore).reversed());

        // 2. 遞迴對所有子節點進行排序
        for (WebNode child : node.getChildren()) {
            sortChildrenRecursive(child);
        }
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

        // 2. 【核心新增】遞迴排序每個 WebNode 樹中的所有子節點
        rootNodes.forEach(this::sortChildrenRecursive);

        // 3. 根據 totalScore 進行降序排列
        rootNodes.sort(Comparator.comparing(WebNode::getTotalScore).reversed());

        System.out.println("[Ranking Server] 排序完成。");
        return rootNodes;
    }

    // 【刪除】移除 searchAndRank 方法，該流程將移至 SearchManager
}
