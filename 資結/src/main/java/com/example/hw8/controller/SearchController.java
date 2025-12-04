package com.example.hw8.controller;

import com.example.hw8.service.RankingService; // <-- 依賴變更
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
// 這裡不需要 throws UnsupportedEncodingException

@Controller
public class SearchController {

    // 【核心變動】注入 RankingService
    private final RankingService rankingService;

    public SearchController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/")
    public String index() {
        return "search";
    }

    @PostMapping("/search")
    public String search(@RequestParam("keyword") String keyword, Model model) { // 移除 throws

        // 【核心變動】呼叫 RankingService，回傳 Map<String, String>
        Map<String, String> results = rankingService.searchAndRank(keyword);

        model.addAttribute("keyword", keyword);
        model.addAttribute("results", results);

        return "search";
    }
}