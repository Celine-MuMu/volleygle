/*package com.example.hw8.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
public class KeywordScorer {

    /**
     * 爬取指定網址，計算關鍵字在網頁內容中出現的次數。
     */
   /* public int getPageScore(String url, String keyword) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(5000)
                    .get();

            //String bodyText = doc.text().toLowerCase(); >改成下面的
            // 使用 getPageScore 傳入的 keyword 進行大小寫轉換
            //String lowerKeyword = keyword.toLowerCase(); >疑似沒用了

            // 取得網頁內容，並對網頁內容進行大小寫轉換
            // 這樣可以確保不論網頁上的關鍵字是大寫(VOLLEYBALL)還是小寫(volleyball)，都能被搜尋到
            String bodyText = doc.text().toLowerCase();

            int count = 0;
            int lastIndex = 0;

            while (lastIndex != -1) {
                lastIndex = bodyText.indexOf(keyword, lastIndex);
                if (lastIndex != -1) {
                    count++;
                    lastIndex += keyword.length();
                }
            }

            // 【新增日誌】
            System.out.println("[Scorer Log] 網址: " + url + " | 關鍵字: " + keyword + " | 得分: " + count);
            return count;

        } catch (IOException e) {
            System.err.println("[Keyword Scorer] 連線錯誤，網址: " + url + " | 錯誤: " + e.getMessage());
            return 0; // 連線失敗回傳 0 分
        } catch (Exception e) {
            return 0;
        }
    }
} */ 

package com.example.hw8.service;

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
    public int getPageScore(String url, String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        int totalScore = 0;

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(5000)
                    // 忽略 HTTP 錯誤碼，確保 Jsoup 即使遇到 404/500 也會嘗試返回內容
                    .ignoreHttpErrors(true) 
                    // 忽略內容類型，解決 Unhandled content type 錯誤
                    .ignoreContentType(true)
                    // 使用自訂的 SSL Socket Factory 來忽略憑證錯誤
                    .sslSocketFactory(getSslSocketFactory()) 
                    .get();

            // 1. 標題計分 (5 倍權重)
            String titleText = doc.title().toLowerCase();
            int titleCount = countKeywordOccurrences(titleText, lowerKeyword);
            totalScore += titleCount * 5;

            // 2. 內文計分
            String bodyText = doc.text().toLowerCase();
            int bodyCount = countKeywordOccurrences(bodyText, lowerKeyword);
            
            // 【內容長度檢查邏輯】
            if (bodyText.length() < 500) { 
                // 如果內容少於 500 個字符，可能為工具頁或錯誤頁，內文分數減半。
                // 這樣可以懲罰內容稀疏的網頁，避免其因標題偶爾出現關鍵字而獲得高分。
                totalScore += bodyCount / 2;
            } else {
                // 內容豐富，給予完整的內文分數
                totalScore += bodyCount; 
            }
            
            System.out.println("[Scorer Log] 網址: " + url + " | 關鍵字: " + keyword + " | 總得分: " + totalScore);
            
            return totalScore;

        } catch (IOException e) {
            System.err.println("[Keyword Scorer] 連線錯誤或超時，網址: " + url + " | 錯誤: " + e.getMessage());
            return 0;
        } catch (Exception e) {
            System.err.println("[Keyword Scorer] 其他錯誤，網址: " + url + " | 錯誤: " + e.getMessage());
            return 0;
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
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext.getSocketFactory();
    }
}