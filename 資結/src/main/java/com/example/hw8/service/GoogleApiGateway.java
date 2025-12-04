package com.example.hw8.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
// 這裡不需要 Jsoup, Collectors, LinkedHashMap 等，因為沒有排序和爬取邏輯

@Service
public class GoogleApiGateway { // <-- 類別名稱已更改

    @Value("${google.cse.apiKey}")
    private String apiKey;

    @Value("${google.cse.cx}")
    private String cx;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String API_BASE_URL = "https://www.googleapis.com/customsearch/v1";

    /**
     * 只負責呼叫 Google CSE API，獲取結果。回傳結果為 Map<Title, Link>。
     */
    public Map<String, String> search(String query) {
        Map<String, String> results = new HashMap<>(); // 使用 HashMap 儲存原始結果

        try {
            for (int i = 1; i < 21; i += 10) {
                String q = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
                String url = API_BASE_URL + "?key=" + apiKey +
                        "&cx=" + cx + "&num=10&start=" + i + "&q=" + q;

                Map<String, Object> body = restTemplate.getForObject(url, Map.class);

                if (body != null && body.get("items") instanceof List) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

                    for (Map<String, Object> item : items) {
                        String title = (String) item.get("title");
                        String link = (String) item.get("link");
                        if (title != null && link != null) {
                            results.put(title, link);
                        }
                    }
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("!!! [API Gateway] 呼叫發生錯誤: " + e.getMessage() + " !!!");
        }
        return results;
    }
}