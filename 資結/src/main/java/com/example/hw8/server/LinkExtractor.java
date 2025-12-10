package com.example.hw8.server;

import com.example.hw8.model.WebNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 引入處理 SSL 錯誤所需的類別
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

@Service
public class LinkExtractor {

    // 追蹤已訪問的 URL，避免重複爬取和無限循環 (使用 ConcurrentHashMap 確保線程安全)
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    private final KeywordScorer keywordScorer;

    // 設定爬取的最大深度，防止爬取時間過長
    private static final int MAX_DEPTH = 2; // 深度 0 是根節點，深度 1 是子網頁...

    public LinkExtractor(KeywordScorer keywordScorer) {
        // 透過建構子注入 KeywordScorer 服務
        this.keywordScorer = keywordScorer;
    }

    /**
     * 從根 URL 開始，遞迴地建構 WebNode 樹。
     * 
     * @param url     根 URL
     * @param keyword 關鍵字
     * @return WebNode 根節點
     */
    public WebNode buildWebTree(String url, String keyword) {
        // 在每次新的搜尋開始時，清除 visitedUrls 集合
        visitedUrls.clear();
        try {
            return buildTreeRecursive(url, keyword, 0);
        } catch (Exception e) {
            System.err.println("[Link Extractor] 建構樹時發生整體錯誤: " + e.getMessage());
            return null;
        }
    }

    /**
     * 遞迴建樹的核心方法。
     */
    private WebNode buildTreeRecursive(String url, String keyword, int depth) {

        // 1. 終止條件：達到深度限制或已訪問過
        if (depth > MAX_DEPTH || visitedUrls.contains(url)) {
            return null;
        }

        // 嘗試爬取並將 URL 加入已訪問集合
        visitedUrls.add(url);

        Document doc = null;
        try {
            // 執行網路連線和爬取 (這是從 KeywordScorer 移來的邏輯)
            doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(5000)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .sslSocketFactory(getSslSocketFactory()) // 忽略 SSL 錯誤
                    .get();
        } catch (IOException e) {
            System.err.println("[Link Extractor] 連線錯誤或超時，網址: " + url + " | 錯誤: " + e.getMessage());
            return new WebNode(url, "連線失敗/超時");
        } catch (Exception e) {
            System.err.println("[Link Extractor] 其他連線錯誤，網址: " + url + " | 錯誤: " + e.getMessage());
            return new WebNode(url, "未知錯誤");
        }

        String title = doc.title().isEmpty() ? url : doc.title();
        WebNode currentNode = new WebNode(url, title);

        // 2. 執行計分
        // 將 Document 傳給 KeywordScorer
        int score = keywordScorer.getPageScore(url, keyword, doc);
        currentNode.setScore(score);

        // 3. 提取並遞迴追蹤子連結
        if (depth < MAX_DEPTH) {
            Elements links = doc.select("a[href]");
            String domain = getDomain(url); // 取得當前 URL 的域名，用於站內連結判斷

            for (Element link : links) {
                String absoluteLink = link.attr("abs:href");

                // 判斷是否為「站內連結」或「目標連結」
                if (isValidInternalLink(absoluteLink, domain) && !visitedUrls.contains(absoluteLink)) {

                    // 遞迴調用，建構子樹
                    WebNode childNode = buildTreeRecursive(absoluteLink, keyword, depth + 1);

                    if (childNode != null) {
                        currentNode.addChild(childNode);
                    }
                }
            }
        }

        System.out.println("Node: " + url + " | Depth: " + depth + " | Score: " + score + " | Children: "
                + currentNode.getChildren().size());
        return currentNode;
    }

    // --- 實用工具方法 ---

    /**
     * 創建一個信任所有憑證的 SSL Socket Factory (與 KeywordScorer 中的相同)。
     */
    private static SSLSocketFactory getSslSocketFactory() throws Exception {
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

    /**
     * 提取 URL 的主域名，例如：從 https://www.example.com/page.html 提取 example.com。
     */
    private String getDomain(String url) {
        Pattern pattern = Pattern.compile("^(?:https?://)?(?:www\\.)?([^/]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * 檢查連結是否是有效的站內連結 (排除了錨點、圖片、外部域名等)。
     */
    private boolean isValidInternalLink(String absoluteLink, String rootDomain) {
        // 1. 排除常見的非內容連結
        if (absoluteLink.isEmpty() ||
                absoluteLink.startsWith("#") ||
                absoluteLink.startsWith("javascript:") ||
                absoluteLink.startsWith("mailto:") ||
                absoluteLink.matches(".*\\.(jpg|png|gif|pdf|zip|rar)$")) {
            return false;
        }

        // 2. 確保連結屬於根域名 (站內爬取)
        String linkDomain = getDomain(absoluteLink);
        if (!linkDomain.equals(rootDomain)) {
            return false;
        }

        // 3. 【新增維基百科專用過濾】
        // 排除維基百科的編輯頁面、特殊頁面、分類頁面等非文章內容頁面
        if (rootDomain.contains("wikipedia.org")) {
            if (absoluteLink.contains("Special:") || // 特殊頁面，如 Special:EditPage
                    absoluteLink.contains("action=edit") || // 編輯頁面
                    absoluteLink.contains("/Category:") || // 分類頁面
                    absoluteLink.contains("/Template:") || // 模板頁面
                    absoluteLink.contains("/File:") || // 檔案頁面
                    absoluteLink.contains("/Talk:")) { // 討論頁面
                return false;
            }
        }

        // 4. 排除查詢參數過多的連結 (通常不是主要內容)
        if (absoluteLink.split("\\?").length > 2) {
            return false;
        }

        return true;
    }
}