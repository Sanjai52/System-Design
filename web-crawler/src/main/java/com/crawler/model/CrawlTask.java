package com.crawler.model;

public class CrawlTask {
    private String url;
    private String parentUrl;
    private int depth;

    public CrawlTask() {}

    public CrawlTask(String url, String parentUrl, int depth) {
        this.url = url;
        this.parentUrl = parentUrl;
        this.depth = depth;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getParentUrl() { return parentUrl; }
    public void setParentUrl(String parentUrl) { this.parentUrl = parentUrl; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
}
