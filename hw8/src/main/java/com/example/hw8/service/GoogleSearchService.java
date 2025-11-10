package com.example.hw8.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;

@Service
public class GoogleSearchService {

    @Value("${google.cse.apiKey}")
    private String apiKey;

    @Value("${google.cse.cx}")
    private String cx;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 搜尋 Google Custom Search，回傳 title → link 的 Map
     */
    public Map<String, String> search(String query) {
        Map<String, String> results = new HashMap<>();
        try {
            // URL Encode 關鍵字
            String q = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            // 組成 Google CSE API URL
            String url = "https://www.googleapis.com/customsearch/v1?key=" + apiKey +
                    "&cx=" + cx + "&num=10&q=" + q;

            // 發送 GET 請求
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);

            if (body == null)
                return results;

            // 解析 JSON items
            Object itemsObj = body.get("items");
            if (itemsObj instanceof List) {
                List<?> items = (List<?>) itemsObj;
                for (Object itemObj : items) {
                    if (itemObj instanceof Map) {
                        Map<String, Object> item = (Map<String, Object>) itemObj;
                        String title = item.get("title") != null ? item.get("title").toString() : null;
                        String link = item.get("link") != null ? item.get("link").toString() : null;
                        if (title != null && !title.isEmpty() && link != null && !link.isEmpty()) {
                            results.put(title, link);
                        }
                    }
                }
            }

            final String inputString = query.toLowerCase();
            results = results.entrySet().stream()
                    .sorted((e1, e2) -> {
                        boolean e1Has = e1.getKey().contains(inputString);
                        boolean e2Has = e2.getKey().contains(inputString);
                        return Boolean.compare(!e1Has, !e2Has);
                    })
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }
}
