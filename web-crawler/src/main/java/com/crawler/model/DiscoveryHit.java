package com.crawler.model;

public class DiscoveryHit {

    private String url;
    private String title;
    private String snippet;
    private int matchCount;
    private int depth;

    public DiscoveryHit() {}

    public DiscoveryHit(String url, String title, String snippet, int matchCount, int depth) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.matchCount = matchCount;
        this.depth = depth;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }

    public int getMatchCount() { return matchCount; }
    public void setMatchCount(int matchCount) { this.matchCount = matchCount; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
}
