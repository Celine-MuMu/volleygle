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

    // 排除常見的無效檔案類型和難以爬取的網域 (Sitemap, Facebook, Twitter 等)
    private static final List<String> INVALID_FILTER_TERMS = Arrays.asList(".xml", ".gz", ".pdf", ".zip", ".rss",
            ".sitemap", "xml.gz",
            "facebook.com", "twitter.com", "instagram.com", "t.co");

    /**
     * 呼叫 Google CSE API，獲取最多 50 筆結果。回傳結果為 Map<Title, Link>。
     */
    public Map<String, String> search(String query) {

        // 【DEBUG 輸出】用於確認程式碼使用的 Key/CX 是否正確
        System.out.println("==================== API DEBUG INFO ====================");
        System.out.println("【DEBUG】使用中的 CSE CX: " + this.cx);
        String keyPrefix = this.apiKey != null && this.apiKey.length() >= 5 ? this.apiKey.substring(0, 5)
                : "KEY_NOT_FOUND or SHORT";
        System.out.println("【DEBUG】使用中的 API Key (前 5 碼): " + keyPrefix + "...");
        System.out.println("======================================================");

        Map<String, String> results = new LinkedHashMap<>();

        // 判斷是否為中文，並設定語言限制 (lr=lang_zh-TW)
        String langParam = query.matches(".*[\\u4e00-\\u9fa5]+.*") ? "&lr=lang_zh-TW" : "";

        try {
            // 【核心修正點：查詢數量】從 1 開始，到小於 51 結束，每次遞增 10
            // 總共會執行 5 次呼叫 (start=1, 11, 21, 31, 41)，最多獲取 50 筆結果。
            for (int i = 1; i < 51; i += 10) {
                String q = URLEncoder.encode(query, StandardCharsets.UTF_8.name());

                // num=10 每次獲取 10 筆
                String url = API_BASE_URL + "?key=" + apiKey +
                        "&cx=" + cx + "&num=10&start=" + i + "&q=" + q + langParam;

                Map<String, Object> body = restTemplate.getForObject(url, Map.class);

                if (body != null && body.get("items") instanceof List) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

                    for (Map<String, Object> item : items) {
                        String title = (String) item.get("title");
                        String link = (String) item.get("link");

                        if (title != null && link != null) {

                            // 檢查是否為無效的檔案類型或網域
                            boolean isInvalid = INVALID_FILTER_TERMS.stream()
                                    .anyMatch(term -> link.toLowerCase().contains(term));

                            if (isInvalid) {
                                // 略過無效連結
                                continue;
                            }

                            try {
                                // 嘗試解碼標題
                                String decodedTitle = URLDecoder.decode(title, StandardCharsets.UTF_8.name());
                                results.put(decodedTitle, link);
                            } catch (Exception e) {
                                results.put(title, link);
                            }
                        }
                    }
                } else {
                    // 如果 API 返回的 body 中沒有 items，表示已無更多結果，則退出迴圈
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("!!! [API Gateway] 呼叫發生錯誤: " + e.getMessage() + " !!!");
        }
        return results;
    }
}