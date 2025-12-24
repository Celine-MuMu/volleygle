package com.example.hw8.server;

import com.example.hw8.model.WebNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.DisposableBean;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

@Service
public class LinkExtractor implements DisposableBean {

    private final KeywordScorer keywordScorer;
    private static final int MAX_DEPTH = 1;
    private final ExecutorService executorService = Executors.newFixedThreadPool(40);
    private static final SSLSocketFactory SSL_FACTORY = createSslFactory();

    public LinkExtractor(KeywordScorer keywordScorer) {
        this.keywordScorer = keywordScorer;
    }

    public WebNode buildWebTree(String url, String keyword, int initialRank, String apiTitle) {
        Set<String> localVisited = ConcurrentHashMap.newKeySet();

        // 將 apiTitle 傳入遞迴方法
        WebNode root = buildTreeRecursive(url, keyword, 0, localVisited, apiTitle);

        if (root != null) {
            root.setGoogleRank(initialRank);
        } else {
            root = new WebNode(url, apiTitle); // 失敗時至少有 API 給的漂亮標題
            root.setGoogleRank(initialRank);
        }
        // 每棵樹建立自己獨立的 Set，不再共用
        // Set<String> localVisited = ConcurrentHashMap.newKeySet();

        // System.out.println("[Link Extractor] 開始爬取根節點: " + url);

        // // 直接呼叫遞迴，並在拿到結果後設定 Rank
        // WebNode root = buildTreeRecursive(url, keyword, 0, localVisited);

        // if (root != null) {
        // root.setGoogleRank(initialRank);
        // } else {
        // // 如果爬取失敗，至少回傳一個帶有 URL 和 Rank 的空節點，避免 RankingServer 報錯
        // root = new WebNode(url, "無法存取該網頁");
        // root.setGoogleRank(initialRank);
        // }
        return root;
    }

    // 【修正 3】將 visitedUrls 作為參數傳遞
    private WebNode buildTreeRecursive(String url, String keyword, int depth, Set<String> visited, String apiTitle) {
        if (depth > MAX_DEPTH || visited.contains(url))
            return null;
        visited.add(url);

        try {
            // 增加一些 Log 讓我們知道它真的在動
            System.out.println("  > 深度 " + depth + " 正在爬取: " + url);

            Document doc = Jsoup.connect(url)
                    .userAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(3000)
                    .ignoreHttpErrors(true)
                    .sslSocketFactory(SSL_FACTORY)
                    .get();

            String jsoupTitle = doc.title();
            String finalTitle = jsoupTitle;

            if (url.contains("instagram.com") || url.contains("threads.net") || jsoupTitle.isEmpty()
                    || jsoupTitle.equalsIgnoreCase("Instagram")) {
                finalTitle = apiTitle; // 💖 相信 Google 的眼光
            }

            WebNode node = new WebNode(url, finalTitle);
            node.setScore(keywordScorer.getPageScore(url, keyword, doc));

            if (depth < MAX_DEPTH) {
                Elements links = doc.select("a[href]");
                List<CompletableFuture<WebNode>> futures = links.stream()
                        .map(l -> l.attr("abs:href"))
                        .filter(l -> !l.isEmpty() && !visited.contains(l))
                        .limit(8)
                        .map(l -> CompletableFuture.supplyAsync(
                                () -> buildTreeRecursive(l, keyword, depth + 1, visited, apiTitle), // 傳遞同一個 Set
                                executorService))
                        .collect(Collectors.toList());

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                futures.forEach(f -> {
                    try {
                        WebNode child = f.join();
                        if (child != null)
                            node.addChild(child);
                    } catch (Exception ignored) {
                    }
                });
            }
            return node;
        } catch (Exception e) {
            System.err.println("  ! 爬取失敗 [" + url + "]: " + e.getMessage());
            return null;
        }
    }

    private static SSLSocketFactory createSslFactory() {
        try {
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void destroy() {
        executorService.shutdown();
    }
}