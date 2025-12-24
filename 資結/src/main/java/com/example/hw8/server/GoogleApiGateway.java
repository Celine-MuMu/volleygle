package com.example.hw8.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class GoogleApiGateway {

    @Value("${google.cse.apiKey}")
    private String apiKey;

    @Value("${google.cse.cx}")
    private String cx;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String API_BASE_URL = "https://www.googleapis.com/customsearch/v1";

    // 【優化：擴大過濾名單】根據日誌紀錄，擋掉確定無關的大型站點與檔案格式
    private static final List<String> INVALID_FILTER_TERMS = Arrays.asList(
            ".xml", ".gz", ".zip", ".rss", ".sitemap", ".mht", ".pdf", ".doc", ".docx", ".csv",
            "apple.com", "microsoft.com", "obsidian.md", "kyocera", "taiwanpay", "ubuy",
            "facebook.com/sharer", "twitter.com/intent", "linkedin.com/cws",
            "sina.com.cn", "ncbi.nlm.nih.gov", "stock.finance");

    // 修改後的 GoogleApiGateway.java 片段
    public Map<String, String> search(String query) {
        Map<String, String> results = new LinkedHashMap<>(); // 保持順序

        try {
            for (int i = 1; i < 21; i += 10) {
                // 使用 UriComponentsBuilder 構建 URI，這會防止「二次編碼」
                java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
                        .fromHttpUrl(API_BASE_URL)
                        .queryParam("key", apiKey)
                        .queryParam("cx", cx)
                        .queryParam("q", query) // 直接傳入 "排球"，不要手動 encode
                        .queryParam("num", 10)
                        .queryParam("start", i)
                        .build()
                        .toUri();

                System.out.println("【真正發出的網址】: " + uri.toString());

                // 傳入 URI 物件，RestTemplate 就不會亂動你的編碼了
                Map<String, Object> body = restTemplate.getForObject(uri, Map.class);

                if (body != null && body.containsKey("items")) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
                    for (Map<String, Object> item : items) {
                        String title = (String) item.get("title");
                        String link = (String) item.get("link");

                        if (title != null && link != null) {
                            // 重要：改用 link 當 Key，防止標題重複導致 Reddit 被蓋掉
                            results.put(link, title);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("搜尋失敗: " + e.getMessage());
        }
        return results;
    }
}
