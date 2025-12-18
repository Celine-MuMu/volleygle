
package com.example.hw8.server;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value; // 【新增】引入 Value
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap; // 【新增】引入 ConcurrentHashMap

@Service
public class KeywordScorer {

    // 【新增】固定的秘密關鍵字及其權重
    private static final Map<String, Double> FIXED_SCORING_KEYWORDS = Map.of(
            "排球", 2.0,
            "台灣職業排球聯盟", 1.0, // 給予更高的權重
            "企業聯賽", 1.0,
            "台灣", 0.5);

    // public KeywordScorer(@Value("${scoring.weighted-keywords:}") String
    // weightedKeywordsString) {
    // System.out.println("[Keyword Scorer] 載入固定秘密計分關鍵字: " +
    // FIXED_SCORING_KEYWORDS);
    // }
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

        // // 🏆 【精準擋掉維基百科首頁】
        // // 邏輯：如果網址包含 wikipedia 且 (標題有"首頁" 或 網址有"Wikipedia:首页")
        // if (url.contains("wikipedia.org")) {
        // if (titleText.contains("首頁") || titleText.contains("main page")
        // || url.contains("Wikipedia:%E9%A6%96%E9%A1%B5")) {
        // // 只有當「首頁」裡面完全沒提到我們要的人名時，才給 0 分
        // // 這樣可以防止誤殺（雖然首頁通常本來就沒什麼人名資料）
        // if (!titleText.contains(keyword.toLowerCase().split("\\s+")[0])) {
        // System.out.println("[Keyword Scorer] 已自動過濾維基百科無關首頁: " + url);
        // return 0;
        // }
        // }
        // }
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
        // 新增排球關鍵字檢查
        boolean hasVolleyball = titleText.contains("排球") ||
                bodyText.contains("男排") ||
                bodyText.contains("球員") ||
                bodyText.contains("女排") ||
                bodyText.contains("企聯");

        // 【 B: 強制門檻邏輯】
        // 如果使用者輸入的關鍵字在整個網頁中沒有出現，則直接給 0 分。
        if (!hasUserKeyword && !keyword.trim().isEmpty()) {
            return 0;
        }
        if (!hasVolleyball) {
            return 0;
        }

        try {

            // 1. 【標題計分】 (5 倍權重)
            int titleCount = countKeywordOccurrences(titleText, lowerKeyword);
            totalScore += titleCount * 10;

            // 2. 【內文計分】
            int bodyCount = countKeywordOccurrences(bodyText, lowerKeyword);

            // 如果內容少於 500 個字符，內文分數減半。
            if (bodyText.length() < 500) {
                totalScore += bodyCount * 2;
            } else {
                totalScore += bodyCount * 5;
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

}