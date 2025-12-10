package com.example.hw8.service;

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

    // (一) 過濾列表 1: 用於篩選掉明顯無效的檔案類型
    private static final List<String> INVALID_EXTENSIONS = 
        Arrays.asList(".xml", ".gz", ".pdf", ".zip", ".rss", ".sitemap", "xml.gz");

    // (二) 過濾列表 2: 用於排除系統文件、證書等雜項結果，強化內容相關性
    private static final List<String> EXCLUDE_KEYWORDS =
        Arrays.asList("certificate", "trusted root", "policy", "sitemap", "xml", "json", "watchos", "changelog", "license", "api", "interface", "firmware", "software", "update");
    
    /**
     * 呼叫 Google CSE API 獲取結果。
     * @param query 查詢關鍵字
     * @return 包含結果標題和連結的 Map (Map<Title, Link>)
     */
    public Map<String, String> search(String query) {
        // 使用 LinkedHashMap 來保留 API 返回的原始順序
        Map<String, String> results = new LinkedHashMap<>(); 

        // 1. URL 編碼 (必須處理編碼異常)
        String q;
        try {
            q = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            System.err.println("!!! [API Gateway] 查詢關鍵字編碼發生錯誤: " + e.getMessage() + " !!!");
            q = query; 
        }
        
        // 2. 語言限制邏輯: 僅對中文進行硬性限制，其他語言讓 Google 自動判斷
        String langParam = "";

        // 中文 (漢字: u4e00-u9fa5)
        if (query.matches(".*[\\u4e00-\\u9fa5]+.*")) { 
            // 僅對中文設定硬性限制，優先使用繁體中文
            langParam = "&lr=lang_zh-TW"; 
        }
        // 韓文、日文、英文、俄文等所有其他語言，langParam 保持為 ""。
        // 這樣 Google 會根據關鍵字本身的語義來判斷最相關的語言結果。

        // 3. 分頁呼叫 API，最多取得 50 筆結果
        try {
            // 測試先用2
            for (int i = 1; i < 2; i += 10) { 
                
                String url = API_BASE_URL + "?key=" + apiKey +
                        "&cx=" + cx + "&num=10&start=" + i + "&q=" + q + langParam;

                Map<String, Object> body = restTemplate.getForObject(url, Map.class);

                if (body != null && body.get("items") instanceof List) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

                    for (Map<String, Object> item : items) {
                        String title = (String) item.get("title");
                        String link = (String) item.get("link");
                        
                        if (title != null && link != null) {
                            
                            // (三) 執行過濾邏輯
                            String lowerLink = link.toLowerCase();
                            String lowerTitle = title.toLowerCase();

                            // 檢查是否為無效的檔案類型
                            boolean isInvalidExt = INVALID_EXTENSIONS.stream()
                                    .anyMatch(ext -> lowerLink.contains(ext));

                            // 檢查是否包含排斥關鍵字
                            boolean isExcludedKeyword = EXCLUDE_KEYWORDS.stream()
                                    .anyMatch(keyword -> lowerLink.contains(keyword) || lowerTitle.contains(keyword));

                            if (isInvalidExt || isExcludedKeyword) {
                                // 如果是無效連結或包含排斥關鍵字，則跳過
                                continue; 
                            }
                            
                            try {
                                // (四) 標題解碼
                                String decodedTitle = URLDecoder.decode(title, StandardCharsets.UTF_8.name()); 
                                results.put(decodedTitle, link);
                            } catch (Exception e) {
                                results.put(title, link);
                            }
                        }
                    }
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("!!! [API Gateway] 呼叫或處理結果發生錯誤: " + e.getMessage() + " !!!");
        }
        return results;
    }
}