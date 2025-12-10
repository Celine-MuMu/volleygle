package com.example.hw8.controller;

import com.example.hw8.service.RankingService; // <-- 依賴變更
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class SearchController {

    // 因需要使用排序，所以注入 RankingService
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

        // 呼叫 RankingService，回傳 Map<String, String>
        Map<String, String> results = rankingService.searchAndRank(keyword);

        // 推導相關關鍵字 (Stage 4)
        List<String> relativeKeywords = rankingService.getRelativeKeywords(keyword, results);

        model.addAttribute("keyword", keyword);
        model.addAttribute("results", results);
        model.addAttribute("relativeKeywords", relativeKeywords); // 傳遞給前端

        return "search";
    }
}