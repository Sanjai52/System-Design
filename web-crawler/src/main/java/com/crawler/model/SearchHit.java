package com.crawler.model;

public class SearchHit {

    private String url;
    private String title;
    private String snippet;
    private double score;

    public SearchHit() {}

    public SearchHit(String url, String title, String snippet, double score) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.score = score;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
