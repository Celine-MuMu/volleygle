package com.example.hw8.server;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import java.io.IOException;
// 引入處理 SSL 錯誤所需的類別
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

@Service
public class KeywordScorer {

    /**
     * 爬取指定網址，計算關鍵字在網頁內容中出現的次數，並加入標題權重。
     */
    public int getPageScore(String url, String keyword, Document doc) {
        String lowerKeyword = keyword.toLowerCase();
        int totalScore = 0;

        try {
            // 現在我們直接使用傳入的 Document 進行計分

            // 1. 標題計分 (5 倍權重)
            // doc 由參數傳入，不需要重新取得
            String titleText = doc.title().toLowerCase();
            int titleCount = countKeywordOccurrences(titleText, lowerKeyword);
            totalScore += titleCount * 5;

            // 2. 內文計分
            String bodyText = doc.text().toLowerCase();
            int bodyCount = countKeywordOccurrences(bodyText, lowerKeyword);

            // 【內容長度檢查邏輯】
            if (bodyText.length() < 500) {
                // 如果內容少於 500 個字符，內文分數減半。
                totalScore += bodyCount / 2;
            } else {
                // 內容豐富，給予完整的內文分數
                totalScore += bodyCount;
            }

            return totalScore;

        } catch (Exception e) { // 【保留捕獲一般 Exception】
            // 捕獲在計分邏輯中可能發生的任何運行時錯誤
            System.err.println("[Keyword Scorer] 計分邏輯錯誤，網址: " + url + " | 錯誤: " + e.getMessage());
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