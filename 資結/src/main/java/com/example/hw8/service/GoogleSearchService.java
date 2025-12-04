// package com.example.hw8.service;

// import org.jsoup.Jsoup;
// import org.jsoup.nodes.Document;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.stereotype.Service;
// import org.springframework.web.client.RestTemplate;

// import java.net.URLEncoder;
// import java.nio.charset.StandardCharsets;
// import java.util.*;
// import java.util.stream.Collectors;

// @Service
// public class GoogleSearchService {

// @Value("${google.cse.apiKey}")
// private String apiKey;

// @Value("${google.cse.cx}")
// private String cx;

// private final RestTemplate restTemplate = new RestTemplate();

// public Map<String, String> search(String query) {
// Map<String, String> results = new HashMap<>();
// String lowerQuery = query.toLowerCase();

// System.out.println("1. 開始搜尋關鍵字: " + query); // [偵探 Log]

// try {
// for (int i = 1; i < 21; i += 10) {
// String q = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
// String url = "https://www.googleapis.com/customsearch/v1?key=" + apiKey +
// "&cx=" + cx + "&num=10&start=" + i + "&q=" + q;

// // [偵探 Log] 印出 URL 確定有拼對 (你可以複製這個網址去瀏覽器貼上看看有沒有資料)
// System.out.println("2. 呼叫 Google API: " + url);

// Map<String, Object> body = restTemplate.getForObject(url, Map.class);

// if (body != null && body.get("items") instanceof List) {
// List<Map<String, Object>> items = (List<Map<String, Object>>)
// body.get("items");

// // [偵探 Log] 看看 Google 到底吐了幾筆給我們
// System.out.println("3. Google 回傳了 " + items.size() + " 筆資料 (Start=" + i +
// ")");

// for (Map<String, Object> item : items) {
// String title = (String) item.get("title");
// String link = (String) item.get("link");
// if (title != null && link != null) {
// results.put(title, link);
// }
// }
// } else {
// System.out.println("3. Google 回傳 null 或沒有 items 欄位！可能額度沒了或是 Key 錯了。");
// }
// }

// System.out.println("4. 過濾前總共有: " + results.size() + " 筆");

// // --- 這裡我先把過濾器註解掉，先確定能跑出東西 ---
// // .filter(entry -> entry.getKey().toLowerCase().contains(lowerQuery))

// results = results.entrySet().stream()
// // 暫時拿掉過濾，先測試爬蟲排序
// .sorted((e1, e2) -> {
// String url1 = e1.getValue();
// String url2 = e2.getValue();

// // [偵探 Log] 顯示正在爬哪裡
// System.out.println("5. 正在爬取分析: " + e1.getKey());

// int score1 = getPageScore(url1, lowerQuery);
// int score2 = getPageScore(url2, lowerQuery);

// return Integer.compare(score2, score1);
// })
// .collect(Collectors.toMap(
// Map.Entry::getKey,
// Map.Entry::getValue,
// (e1, e2) -> e1,
// LinkedHashMap::new));

// System.out.println("6. 最終回傳: " + results.size() + " 筆");

// } catch (Exception e) {
// System.out.println("!!! 發生錯誤 !!!"); // [偵探 Log]
// e.printStackTrace();
// }
// return results;
// }
// // public Map<String, String> search(String query) {
// // Map<String, String> results = new HashMap<>();
// // String lowerQuery = query.toLowerCase();

// // try {
// // // --- 1. 多頁搜尋 (迴圈) ---
// // // i=1 代表從第1筆開始，i=11 代表從第11筆開始。
// // // 這裡我設成只抓兩頁 (共20筆)，因為抓太多會很慢！
// // for (int i = 1; i < 21; i += 10) {

// // String q = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
// // String url = "https://www.googleapis.com/customsearch/v1?key=" + apiKey +
// // "&cx=" + cx + "&num=10&start=" + i + "&q=" + q;

// // Map<String, Object> body = restTemplate.getForObject(url, Map.class);

// // // 把這一頁的 10 筆資料撈出來存入 results
// // if (body != null && body.get("items") instanceof List) {
// // List<Map<String, Object>> items = (List<Map<String, Object>>)
// // body.get("items");
// // for (Map<String, Object> item : items) {
// // String title = (String) item.get("title");
// // String link = (String) item.get("link");
// // if (title != null && link != null) {
// // results.put(title, link);
// // }
// // }
// // }
// // }

// // // --- 2. 嚴格過濾 & 排序 & 查看子網頁 ---
// // results = results.entrySet().stream()
// // // (A) 先過濾：標題如果連關鍵字都沒有，直接不要了 (省時間，不進去爬了)
// // .filter(entry -> entry.getKey().toLowerCase().contains(lowerQuery))

// // // (B) 排序邏輯 (包含爬蟲)
// // .sorted((e1, e2) -> {
// // String url1 = e1.getValue();
// // String url2 = e2.getValue();

// // // 呼叫我們下面寫的「爬蟲計分」方法
// // int score1 = getPageScore(url1, lowerQuery);
// // int score2 = getPageScore(url2, lowerQuery);

// // // 分數高的排前面
// // return Integer.compare(score2, score1);
// // })
// // .collect(Collectors.toMap(
// // Map.Entry::getKey,
// // Map.Entry::getValue,
// // (e1, e2) -> e1,
// // LinkedHashMap::new));

// // } catch (Exception e) {
// // e.printStackTrace();
// // }
// // return results;
// // }

// /**
// * 這就是「查看子網頁」的核心！
// * 它會真的連線進去該網址，計算關鍵字在網頁內容中出現了幾次。
// */
// private int getPageScore(String url, String keyword) {
// try {
// // 設定 3秒 (3000ms) 超時，避免網頁跑太久卡住你的程式
// Document doc = Jsoup.connect(url)
// .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)") // 偽裝成瀏覽器
// .timeout(3000)
// .get();

// // 抓取網頁的所有「純文字」內容 (去掉 HTML 標籤)
// String bodyText = doc.text().toLowerCase();

// // 簡單演算法：算關鍵字出現幾次？
// // 這裡用 split 切割來計算出現次數
// int count = bodyText.split(keyword, -1).length - 1;

// System.out.println("爬取: " + url + " -> 關鍵字出現 " + count + " 次");
// return count; // 回傳出現次數當作分數

// } catch (Exception e) {
// // 如果連線失敗 (例如網站擋爬蟲，或網址壞掉)，就給 0 分
// System.out.println("爬取失敗: " + url);
// return 0;
// }
// }
// }

// // package com.example.hw8.service;

// // import org.springframework.beans.factory.annotation.Value;
// // import org.springframework.stereotype.Service;
// // import org.springframework.web.client.RestTemplate;

// // import java.io.UnsupportedEncodingException;
// // import java.net.URLEncoder;
// // import java.nio.charset.StandardCharsets;
// // import java.util.LinkedHashMap;
// // import java.util.List;
// // import java.util.Map;
// // import java.util.stream.Collectors;
// // import java.util.HashMap;

// // @Service
// // public class GoogleSearchService {

// // @Value("${google.cse.apiKey}")
// // private String apiKey;

// // @Value("${google.cse.cx}")
// // private String cx;

// // private final RestTemplate restTemplate = new RestTemplate();

// // /**
// // * 搜尋 Google Custom Search，回傳 title → link 的 Map
// // */
// // public Map<String, String> search(String query) {
// // Map<String, String> results = new HashMap<>();
// // try {
// // // URL Encode 關鍵字
// // String q = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
// // // 組成 Google CSE API URL
// // String url = "https://www.googleapis.com/customsearch/v1?key=" + apiKey +
// // "&cx=" + cx + "&num=10&q=" + q;

// // // 發送 GET 請求
// // Map<String, Object> body = restTemplate.getForObject(url, Map.class);

// // if (body == null)
// // return results;

// // // 解析 JSON items
// // Object itemsObj = body.get("items");
// // if (itemsObj instanceof List) {
// // List<?> items = (List<?>) itemsObj;
// // for (Object itemObj : items) {
// // if (itemObj instanceof Map) {
// // Map<String, Object> item = (Map<String, Object>) itemObj;
// // String title = item.get("title") != null ? item.get("title").toString() :
// // null;
// // String link = item.get("link") != null ? item.get("link").toString() :
// null;
// // if (title != null && !title.isEmpty() && link != null && !link.isEmpty())
// {
// // results.put(title, link);
// // }
// // }
// // }
// // }

// // // 根據關鍵字排序，包含關鍵字的 title 排前面
// // final String inputString = query.toLowerCase();
// // results = results.entrySet().stream()
// // .sorted((e1, e2) -> {
// // String link1 = e1.getKey().toLowerCase();
// // String link2 = e2.getKey().toLowerCase();
// // // getKey() 是 title
// // // getValue() 是 link
// // int score1 = 0;
// // int score2 = 0;

// // if (link1.contains("排球")) {
// // score1 += 10;
// // }
// // if (link2.contains("排球")) {
// // score2 += 10;
// // }

// // if (e1.getValue().contains("tpvl.tw")) {
// // score1 += 5;
// // }
// // if (e2.getValue().contains("tpvl.tw")) {
// // score2 += 5;
// // }

// // if (link1.contains(inputString)) {
// // score1 += 10;
// // }
// // if (link2.contains(inputString)) {
// // score2 += 10;
// // }

// // return Integer.compare(score2, score1);
// // })
// // .collect(Collectors.toMap(
// // Map.Entry::getKey,
// // Map.Entry::getValue,
// // (e1, e2) -> e1,
// // LinkedHashMap::new));

// // } catch (Exception e) {
// // e.printStackTrace();
// // }
// // return results;
// // }
// // }
