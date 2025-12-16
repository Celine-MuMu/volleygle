
package com.example.hw8.server;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value; // 【新增】引入 Value
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap; // 【新增】引入 ConcurrentHashMap

// 引入處理 SSL 錯誤所需的類別
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

@Service
public class KeywordScorer {

    // 【新增】固定的秘密關鍵字及其權重
    private static final Map<String, Double> FIXED_SCORING_KEYWORDS = Map.of(
            "排球", 2.0,
            "台灣職業排球聯盟", 1.0, // 給予更高的權重
            "volleyball", 1.0,
            "企業聯賽", 1.2);

    public KeywordScorer(@Value("${scoring.weighted-keywords}") String weightedKeywordsString) {
        System.out.println("[Keyword Scorer] 載入固定秘密計分關鍵字: " + FIXED_SCORING_KEYWORDS);
    }

    /**
     * 爬取指定網址，計算關鍵字在網頁內容中出現的次數，並加入標題權重。
     */
    public int getPageScore(String url, String keyword, Document doc) {
        String lowerKeyword = keyword.toLowerCase();
        int totalScore = 0;

        String titleText = doc.title().toLowerCase();
        String bodyText = doc.text().toLowerCase();

        try {

            // 1. 【標題計分】 (5 倍權重)
            int titleCount = countKeywordOccurrences(titleText, lowerKeyword);
            totalScore += titleCount * 5;

            // 2. 【內文計分】
            int bodyCount = countKeywordOccurrences(bodyText, lowerKeyword);

            // 如果內容少於 500 個字符，內文分數減半。
            if (bodyText.length() < 500) {
                totalScore += bodyCount / 2;
            } else {
                totalScore += bodyCount;
            }

            // 3. 【偷偷家的關鍵字計分】: 使用 FIXED_SCORING_KEYWORDS
            for (Map.Entry<String, Double> entry : FIXED_SCORING_KEYWORDS.entrySet()) {
                String fixedKeyword = entry.getKey();
                Double weight = entry.getValue();

                int occurrenceCount = countKeywordOccurrences(bodyText, fixedKeyword);
                totalScore += (int) (occurrenceCount * weight);
            }
            return totalScore;

        } catch (Exception e) {
            System.err.println("[Keyword Scorer] 計分邏輯錯誤，網址: " + url + " | 錯誤: " +
                    e.getMessage());
            return 0; // 計分失敗回傳 0 分
        }
    }

    /**
     * 計算關鍵字在原始文本中出現的次數。
     */
    private int countKeywordOccurrences(String source, String keyword) {
        int count = 0;
        int lastIndex = 0;
        while (lastIndex != -1) {
            lastIndex = source.indexOf(keyword, lastIndex);
            if (lastIndex != -1) {
                count++;
                lastIndex += keyword.length();
            }
        }
        return count;
    }

    /**
     * 創建一個信任所有憑證的 SSL Socket Factory，用來繞過 SSL/TLS 錯誤。
     */
    private static SSLSocketFactory getSslSocketFactory() throws Exception {
        // 信任管理器：信任所有憑證
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext.getSocketFactory();
    }
}