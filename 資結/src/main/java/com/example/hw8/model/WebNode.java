package com.example.hw8.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 樹狀結構中的一個節點，代表一個 URL 頁面。
 */
public class WebNode {
    private String url;
    private String title;
    private int score = 0; // 該頁面本身的關鍵字分數
    private int totalScore = 0; // 該頁面及其所有子頁面的總分數 (用於排名)
    private List<WebNode> children = new ArrayList<>(); // 該頁面連結到的子網頁列表

    public WebNode(String url, String title) {
        this.url = url;
        this.title = title;
    }

    // Getters and Setters
    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public List<WebNode> getChildren() {
        return children;
    }

    public void addChild(WebNode child) {
        this.children.add(child);
    }

    @Override
    public String toString() {
        return "URL: " + url + ", Score: " + score + ", TotalScore: " + totalScore + ", Children: " + children.size();
    }
}