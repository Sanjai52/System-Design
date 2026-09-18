package com.crawler.model;

import java.time.LocalDateTime;

public class UrlResult {

    private String url;
    private CrawlStatus status;
    private int discoveredLinks;
    private String error;
    private LocalDateTime timestamp;

    public UrlResult() {}

    public UrlResult(String url, CrawlStatus status, int discoveredLinks) {
        this.url = url;
        this.status = status;
        this.discoveredLinks = discoveredLinks;
        this.timestamp = LocalDateTime.now();
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public CrawlStatus getStatus() { return status; }
    public void setStatus(CrawlStatus status) { this.status = status; }

    public int getDiscoveredLinks() { return discoveredLinks; }
    public void setDiscoveredLinks(int discoveredLinks) { this.discoveredLinks = discoveredLinks; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    private String parentUrl;
    private int depth;
    private java.util.List<String> childUrls = new java.util.ArrayList<>();

    public String getParentUrl() { return parentUrl; }
    public void setParentUrl(String parentUrl) { this.parentUrl = parentUrl; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    public java.util.List<String> getChildUrls() { return childUrls; }
    public void setChildUrls(java.util.List<String> childUrls) {
        this.childUrls = childUrls != null ? childUrls : new java.util.ArrayList<>();
    }
}
