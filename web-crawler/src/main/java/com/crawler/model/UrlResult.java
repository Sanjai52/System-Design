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
}
