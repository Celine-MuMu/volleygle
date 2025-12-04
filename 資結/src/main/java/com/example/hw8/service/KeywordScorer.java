package com.example.hw8.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
public class KeywordScorer {

    /**
     * 爬取指定網址，計算關鍵字在網頁內容中出現的次數。
     */
    public int getPageScore(String url, String keyword) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(3000)
                    .get();

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

            return count;

        } catch (IOException e) {
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}