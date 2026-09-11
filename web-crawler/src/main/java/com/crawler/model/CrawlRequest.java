package com.crawler.model;

public class CrawlRequest {

    private String url;
    private int maxPages = 50;

    public CrawlRequest() {}

    public CrawlRequest(String url, int maxPages) {
        this.url = url;
        this.maxPages = maxPages;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getMaxPages() { return maxPages; }
    public void setMaxPages(int maxPages) { this.maxPages = maxPages; }
}
