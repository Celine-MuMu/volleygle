package com.example.hw8.controller;

import com.example.hw8.service.GoogleSearchService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.UnsupportedEncodingException;
import java.util.Map;

@Controller
public class SearchController {

    private final GoogleSearchService googleSearchService;

    public SearchController(GoogleSearchService googleSearchService) {
        this.googleSearchService = googleSearchService;
    }

    @GetMapping("/")
    public String index() {
        return "search";
    }

    @PostMapping("/search")
    public String search(@RequestParam("keyword") String keyword, Model model) throws UnsupportedEncodingException {
        Map<String, String> results = googleSearchService.search(keyword);
        model.addAttribute("keyword", keyword);
        model.addAttribute("results", results);
        return "search";
    }
}
