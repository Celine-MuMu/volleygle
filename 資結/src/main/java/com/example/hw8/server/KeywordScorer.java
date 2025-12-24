
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
            "企業聯賽", 1.0,
            "台灣", 0.5);

    public KeywordScorer() {
        System.out.println("[Keyword Scorer] 載入固定秘密計分關鍵字: " + FIXED_SCORING_KEYWORDS);
    }

    /**
     * 爬取指定網址，計算關鍵字在網頁內容中出現的次數，並加入標題權重。
     */
    public int getPageScore(String url, String keyword, Document doc) {

        int totalScore = 0;

        String titleText = doc.title().toLowerCase();

        // 🏆 【修正點】除了 doc.text()，額外抓取 Meta Description
        StringBuilder contentToScore = new StringBuilder(doc.text().toLowerCase());

        // 抓取 <meta name="description">
        String metaDesc = doc.select("meta[name=description]").attr("content").toLowerCase();
        // 抓取 <meta property="og:description"> (社群平台最愛用這個)
        String ogDesc = doc.select("meta[property=og:description]").attr("content").toLowerCase();

        contentToScore.append(" ").append(metaDesc).append(" ").append(ogDesc);
        String bodyText = contentToScore.toString();

        System.out.println("【DEBUG】網址: " + url + " | 抓到的文字長度: " + bodyText.length() + "| 分數：" + totalScore);

        // 將 keyword 拆解成單字列表 (處理空格)
        // 例如 "吳宗軒 排球" -> ["吳宗軒", "排球"]
        String[] keywordParts = keyword.toLowerCase().split("\\s+");

        // 判斷內容有沒有關鍵字
        boolean hasUserKeyword = false;
        for (String part : keywordParts) {
            if (part.isEmpty())
                continue;
            int partCountInTitle = countKeywordOccurrences(titleText, part);
            int partCountInBody = countKeywordOccurrences(bodyText, part);
            if (partCountInTitle + partCountInBody > 0) {
                hasUserKeyword = true;
            }
            // 使用者關鍵字的權重 (標題 10 倍, 內文依長度決定2倍或5倍)
            totalScore += partCountInTitle * 10;
            if (bodyText.length() < 500) {
                totalScore += partCountInBody * 2;
            } else {
                totalScore += partCountInBody * 5;
            }
        }

        try {
            // 偷偷家的關鍵字計分 FIXED_SCORING_KEYWORDS
            for (Map.Entry<String, Double> entry : FIXED_SCORING_KEYWORDS.entrySet()) {
                String fixedKeyword = entry.getKey();
                Double weight = entry.getValue();

                int occurrenceCount = countKeywordOccurrences(bodyText, fixedKeyword);
                totalScore += (int) (occurrenceCount * weight);
            }
            // 偷偷幫社群媒體加分(因為我爬不到)
            if (url.toLowerCase().contains("instagram.com") || url.toLowerCase().contains("threads.net")
                    || url.toLowerCase().contains("threads.com")) {
                totalScore += 50; // 給它 50 分的基本分
                hasUserKeyword = true; // 強制讓它不被 /10，因為社群網站通常是你要的
            }

            // 如果內容完全沒有使用者關鍵字，分數/10
            if (!hasUserKeyword) {
                totalScore /= 10;
            }
            return totalScore;

        } catch (Exception e) {
            System.err.println("[Keyword Scorer] 計分邏輯錯誤，網址: " + url + " | 錯誤: " +
                    e.getMessage());
            return 0; // 計分失敗回傳 0 分
        }
    }

    // 計算關鍵字在原始文本中出現的次數。
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