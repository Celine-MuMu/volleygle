package com.example.hw8.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KeywordAnalyzer {

    // 簡單的停用詞列表 (Stop Words) - 您可能需要更長的列表
    private static final Set<String> STOP_WORDS = Set.of(
        "的", "是", "在", "和", "有", "也", "了", "等", "為", "個", "會", "能", "可以", "我們", 
        "and", "the", "a", "an", "is", "of", "to", "in", "for", "with", "from", "on"
    );

    /**
     * 從一組高排名網頁標題和內容中提取出最常出現的前 N 個詞彙。
     */
    public List<String> getRelevantKeywords(String text, String originalQuery, int limit) {
        // 1. 文本預處理 (移除標點符號，轉小寫)
        String cleanText = text.toLowerCase()
                               .replaceAll("[^a-z0-9\\u4e00-\\u9fa5\\s]", " "); // 只保留字母、數字、中文和空格

        // 2. 分詞 (簡單地以空格分隔，對於英文有效；中文需要更複雜的分詞器，如 Jieba)
        String[] words = cleanText.split("\\s+");

        // 3. 詞頻統計
        Map<String, Integer> frequencyMap = new HashMap<>();
        for (String word : words) {
            // 過濾掉停用詞和長度小於 2 的詞彙
            if (word.length() < 2 || STOP_WORDS.contains(word) || originalQuery.contains(word)) {
                continue;
            }
            frequencyMap.put(word, frequencyMap.getOrDefault(word, 0) + 1);
        }

        // 4. 排序並取前 N 個
        return frequencyMap.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
}