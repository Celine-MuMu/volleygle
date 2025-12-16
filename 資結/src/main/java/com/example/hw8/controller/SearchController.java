package com.example.hw8.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.stream.Collectors;

import com.example.hw8.server.RankingServer;
import com.example.hw8.model.WebNode;
import com.example.hw8.server.SearchManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
// 這裡不需要 throws UnsupportedEncodingException

@Controller
public class SearchController {

    // 注入 RankingService
    private final SearchManager searchManager;

    public SearchController(SearchManager searchManager) {
        this.searchManager = searchManager;
    }

    @GetMapping("/")
    public String index() {
        return "search";
    }

    @PostMapping("/search")
    public String search(@RequestParam("keyword") String keyword, Model model) {
        // 手動塞入的 URL (manual seed)
        List<String> manualSeeds = new ArrayList<>();
        manualSeeds.add("https://www.ctvba.org.tw/");
        manualSeeds.add("https://www.tpvl.tw/");

        // 呼叫 SearchManager 的新方法
        List<WebNode> results = searchManager.performTreeSearchAndRank(keyword, manualSeeds);

        // 只保留 TotalScore > 0 的根節點
        List<WebNode> filteredResults = results.stream().filter(node -> node.getTotalScore() > 0)
                .collect(Collectors.toList());

        model.addAttribute("keyword", keyword);
        model.addAttribute("results", filteredResults);

        return "search";

    }
}