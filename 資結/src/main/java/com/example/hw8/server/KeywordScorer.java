
package com.example.hw8.server;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class KeywordScorer {

    // 【新增】固定的秘密關鍵字及其權重
    private static final Map<String, Double> FIXED_SCORING_KEYWORDS = Map.of(
            "排球", 2.0,
            "台灣職業排球聯盟", 1.0, // 給予更高的權重
            "企業聯賽", 1.0);

    public KeywordScorer() {
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

        System.out.println("【DEBUG】網址: " + url + " | 抓到的文字長度: " + bodyText.length());

        // 🏆 【修正點 A: 計算使用者關鍵字出現總次數 (門檻)】
        // 2. 【核心修正】將 keyword 拆解成單字列表 (處理空格)
        // 例如 "吳宗軒 排球" -> ["吳宗軒", "排球"]
        String[] keywordParts = keyword.toLowerCase().split("\\s+");

        boolean hasUserKeyword = false;
        for (String part : keywordParts) {
            if (part.isEmpty())
                continue; // 跳過空字串
            int partCountInTitle = countKeywordOccurrences(titleText, part);
            int partCountInBody = countKeywordOccurrences(bodyText, part);
            if (partCountInTitle + partCountInBody > 0) {
                hasUserKeyword = true;
            }
            // 使用者關鍵字的權重 (標題 10 倍, 內文依長度計分)
            totalScore += partCountInTitle * 10;
            if (bodyText.length() < 500) {
                totalScore += partCountInBody * 2;
            } else {
                totalScore += partCountInBody * 5;
            }
        }

        try {

            // // 1. 【標題計分】
            // int titleCount = countKeywordOccurrences(titleText, lowerKeyword);
            // totalScore += titleCount * 10;

            // // 2. 【內文計分】
            // int bodyCount = countKeywordOccurrences(bodyText, lowerKeyword);

            // 如果內容少於 500 個字符，內文分數減半。
            // if (bodyText.length() < 500) {
            // totalScore += bodyCount * 2;
            // } else {
            // totalScore += bodyCount * 5;
            // }

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

}